(ns blaze.db.resource-store.cassandra
  (:require
    [blaze.anomaly :refer [ex-anom]]
    [blaze.async.comp :as ac]
    [blaze.db.resource-store :as rs]
    [blaze.db.resource-store.cassandra.spec]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.module :refer [reg-collector]]
    [cheshire.core :as cheshire]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [prometheus.alpha :as prom :refer [defhistogram]]
    [taoensso.timbre :as log])
  (:import
    [com.datastax.oss.driver.api.core
     ConsistencyLevel CqlSession DefaultConsistencyLevel]
    [com.datastax.oss.driver.api.core.cql
     AsyncResultSet PreparedStatement Row SimpleStatement Statement]
    [java.io Closeable]
    [java.net InetSocketAddress]
    [java.nio ByteBuffer]
    [java.util.concurrent TimeUnit]
    [com.datastax.oss.driver.api.core.config
     DriverConfigLoader OptionsMap TypedDriverOption]))


(set! *warn-on-reflection* true)


(defhistogram duration-seconds
  "Durations in Cassandra resource store."
  {:namespace "blaze"
   :subsystem "db"
   :name "resource_store_cassandra_duration_seconds"}
  (take 12 (iterate #(* 2 %) 0.0001))
  "op")


(defhistogram resource-bytes
  "Stored resource sizes in bytes in Cassandra resource store."
  {:namespace "blaze"
   :subsystem "db"
   :name "resource_store_cassandra_resource_bytes"}
  (take 16 (iterate #(* 2 %) 32)))


(defn- retryable? [{::anom/keys [category]}]
  (= ::anom/not-found category))


(defn- retry* [future-fn max-retries num-retry]
  (-> (future-fn)
      (ac/handle
        (fn [result e]
          (cond
            (retryable? (ex-data (ex-cause e)))
            (if (= num-retry max-retries)
              (ac/failed-future e)
              (let [delay (long (* (Math/pow 2.0 num-retry) 20))]
                (log/warn (format "Wait %d ms before retrying an action." delay))
                (-> (ac/future)
                    (ac/complete-on-timeout!
                      nil delay TimeUnit/MILLISECONDS)
                    (ac/then-compose
                      (fn [_] (retry* future-fn max-retries (inc num-retry)))))))
            (some? e)
            (ac/failed-future e)

            :else
            (ac/completed-future result))))
      (ac/then-compose identity)))


(defn- retry
  "Please be aware that `num-retries` shouldn't be higher than the max stack
  depth. Otherwise the CompletionStage would fail with a StackOverflowException."
  [future-fn num-retries]
  (retry* future-fn num-retries 0))


(defn- bind-get [^PreparedStatement statement hash]
  (.bind statement (object-array [(str hash)])))


(defn- execute [^CqlSession session op ^Statement statement]
  (let [timer (prom/timer duration-seconds op)]
    (-> (.executeAsync session statement)
        (ac/when-complete (fn [_ _] (prom/observe-duration! timer))))))


(defn- parse-msg [hash e]
  (format "Error while parsing resource content with hash `%s`: %s" hash
          (ex-message e)))


(defn- parse-anom [hash e]
  (ex-anom #::anom{:category ::anom/fault :message (parse-msg hash e)}))


(defn- parse-cbor [bytes hash]
  (try
    (cheshire/parse-cbor bytes keyword)
    (catch Exception e
      (throw (parse-anom hash e)))))


(defn- read-content [^AsyncResultSet rs hash]
  (if-let [^Row row (.one rs)]
    (fhir-spec/conform-cbor (parse-cbor (.array (.getByteBuffer row 0)) hash))
    (throw (ex-anom {::anom/category ::anom/not-found}))))


(defn- execute-get* [session statement hash]
  (-> (execute session "get" (bind-get statement hash))
      (ac/then-apply-async #(read-content % hash))))


(defn- execute-get [session statement hash]
  (-> (retry #(execute-get* session statement hash) 5)
      (ac/exceptionally
        (fn [e]
          (if (= ::anom/not-found (::anom/category (ex-data (ex-cause e))))
            nil
            (throw e))))))


(defn- execute-multi-get [session get-statement hashes]
  (mapv #(ac/->completable-future (execute-get session get-statement %)) hashes))


(defn- bind-put [^PreparedStatement statement hash resource]
  (let [content (ByteBuffer/wrap (cheshire/generate-cbor (fhir-spec/unform-cbor resource)))]
    (prom/observe! resource-bytes (.capacity content))
    (.bind statement (object-array [(str hash) content]))))


(defn- execute-put [session statement [hash resource]]
  (execute session "put" (bind-put statement hash resource)))


(defn- execute-multi-put [session statement entries]
  (map #(ac/->completable-future (execute-put session statement %)) entries))


(defn- zipmap-found [hashes resources]
  (loop [map {}
         [hash & hashes] hashes
         [resource & resources] resources]
    (if hash
      (if resource
        (recur (assoc map hash resource) hashes resources)
        (recur map hashes resources))
      map)))


(deftype CassandraResourceStore [session get-statement put-statement]
  rs/ResourceLookup
  (-get [_ hash]
    (log/trace "get" hash)
    (execute-get session get-statement hash))

  (-multi-get [_ hashes]
    (log/trace "multi-get" (count hashes) "resource(s)")
    (let [futures (execute-multi-get session get-statement hashes)]
      (-> (ac/all-of futures)
          (ac/then-apply
            (fn [_] (zipmap-found hashes (map deref futures)))))))

  rs/ResourceStore
  (-put [_ entries]
    (log/trace "put" (count entries) "entries")
    (ac/all-of (execute-multi-put session put-statement entries)))

  Closeable
  (close [_]
    (.close ^CqlSession session)))


(defn- build-contact-points [contact-points]
  (map
    #(let [[hostname port] (str/split % #":" 2)]
       (InetSocketAddress. ^String hostname (Integer/parseInt port)))
    (str/split contact-points #",")))


(def ^:private ^SimpleStatement get-statement
  "The get statement retrieves the content according to the `hash`.

  The consistency level is set to ONE because reads are retried if nothing was
  found. Because rows are never updated, strong consistency isn't needed."
  (-> (SimpleStatement/builder "select content from resources where hash = ?")
      (.setConsistencyLevel ConsistencyLevel/ONE)
      (.build)))


(defn- prepare-get-statement [^CqlSession session]
  (.prepare session get-statement))


(defn- put-statement
  "The put statement upserts the `hash` and `content` of a resource into the
  `resources` table.

  The consistency level can be set to ONE or TWO depending on durability
  requirements. Strong consistency of QUORUM isn't needed, because rows are
  never updated. Reads will retry until they see a hash."
  ^SimpleStatement
  [consistency-level]
  (-> (SimpleStatement/builder "insert into resources (hash, content) values (?, ?)")
      (.setConsistencyLevel consistency-level)
      (.build)))


(defn- prepare-put-statement [^CqlSession session consistency-level]
  (.prepare session (put-statement consistency-level)))


(defn new-cassandra-resource-store
  [contact-points key-space put-consistency-level
   max-concurrent-requests max-request-queue-size]
  (let [options (doto (OptionsMap/driverDefaults)
                  (.put TypedDriverOption/REQUEST_THROTTLER_CLASS "ConcurrencyLimitingRequestThrottler")
                  (.put TypedDriverOption/REQUEST_THROTTLER_MAX_CONCURRENT_REQUESTS (int max-concurrent-requests))
                  (.put TypedDriverOption/REQUEST_THROTTLER_MAX_QUEUE_SIZE (int max-request-queue-size)))
        session (-> (CqlSession/builder)
                    (.withConfigLoader (DriverConfigLoader/fromMap options))
                    (.addContactPoints (build-contact-points contact-points))
                    (.withLocalDatacenter "datacenter1")
                    (.withKeyspace ^String key-space)
                    (.build))]
    (->CassandraResourceStore
      session
      (prepare-get-statement session)
      (prepare-put-statement session put-consistency-level))))


(defmethod ig/pre-init-spec ::rs/cassandra [_]
  (s/keys :req-un [::contact-points ::key-space]
          :opt-un [::put-consistency-level
                   ::max-concurrent-requests
                   ::max-request-queue-size]))


(defn- init-msg [contact-points put-consistency-level]
  (format "Open cassandra backed resource store with a put consistency level of %s and the following contact points: %s"
          put-consistency-level contact-points))


(defmethod ig/init-key ::rs/cassandra
  [_ {:keys [contact-points key-space put-consistency-level
             max-concurrent-read-requests max-read-request-queue-size]
      :or {put-consistency-level "TWO"
           max-concurrent-read-requests 1024
           max-read-request-queue-size 100000}}]
  (log/info (init-msg contact-points put-consistency-level))
  (new-cassandra-resource-store
    contact-points key-space
    (DefaultConsistencyLevel/valueOf put-consistency-level)
    max-concurrent-read-requests
    max-read-request-queue-size))


(defmethod ig/halt-key! ::rs/cassandra
  [_ store]
  (.close ^Closeable store))


(derive ::rs/cassandra :blaze.db/resource-store)


(reg-collector ::duration-seconds
  duration-seconds)


(reg-collector ::resource-bytes
  resource-bytes)
