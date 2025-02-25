(ns blaze.fhir-client
  (:require
    [blaze.async.comp :as ac]
    [blaze.async.flow :as flow]
    [blaze.fhir-client.impl :as impl])
  (:import
    [java.net Authenticator PasswordAuthentication]
    [java.net.http HttpClient]
    [java.util.concurrent Flow$Publisher])
  (:refer-clojure :exclude [read spit update]))


(set! *warn-on-reflection* true)


(defn client
  ([base-uri]
   (client impl/default-http-client base-uri))
  ([http-client base-uri]
   {:http-client http-client
    :base-uri base-uri}))


(defn- password-authenticator [username password]
  (proxy [Authenticator] []
    (getPasswordAuthentication []
      (PasswordAuthentication. username (.toCharArray ^String password)))))


(defn- http-client-with-authenticator
  "Returns a HttpClient with given Authenticator set for authentication."
  [authenticator]
  (-> (HttpClient/newBuilder)
      (.authenticator authenticator)
      .build))


(defn authenticated-client
  "Returns a FHIR client configured to use the given credentials with basic
  authentication."
  [base-uri username password]
  (-> (password-authenticator username password)
    http-client-with-authenticator
    (client base-uri)))


(defn metadata
  "Returns a CompletableFuture that completes with the CapabilityStatement in
  case of success or completes exceptionally with an anomaly in case of an error."
  {:arglists '([client])}
  [{:keys [base-uri http-client]}]
  (impl/fetch http-client (str base-uri "/metadata")))


(defn read
  "Returns a CompletableFuture that completes with the resource with `type` and
  `id` in case of success or completes exceptionally with an anomaly in case of
  an error."
  {:arglists '([client type id])}
  [{:keys [base-uri http-client]} type id]
  (impl/fetch http-client (str base-uri "/" type "/" id)))


(defn update
  "Returns a CompletableFuture that completes with `resource` updated."
  [client resource]
  (impl/update client resource))


(defn- query-str [params]
  (interpose "&" (map (fn [[k v]] (str k "=" v)) params)))


(defn- search-type-uri [base-uri type params]
  (if (seq params)
    (apply str base-uri "/" type "?" (query-str params))
    (str base-uri "/" type)))


(defn search-type-publisher
  "Returns a Publisher that produces a Bundle per page of resources with `type`.

  Use `resource-processor` to transform the pages to individual resources. Use
  `search-type` if you simply want to fetch all resources."
  {:arglists '([client type params])}
  [{:keys [base-uri http-client]} type params]
  (reify Flow$Publisher
    (subscribe [_ subscriber]
      (->> (search-type-uri base-uri type params)
           (impl/paging-subscription subscriber http-client)
           (flow/on-subscribe! subscriber)))))


(defn resource-processor
  "Returns a Processor that produces resources from Bundle entries produced."
  []
  (flow/mapcat #(map :resource (:entry %))))


(defn search-type
  "Returns a CompletableFuture that completes with all resource of `type` in
  case of success or completes exceptionally with an anomaly in case of an
  error."
  ([client type]
   (search-type client type {}))
  ([client type params]
   (let [src (search-type-publisher client type params)
         pro (resource-processor)
         dst (flow/collect pro)]
     (flow/subscribe! src pro)
     dst)))


(defn- search-system-uri [base-uri params]
  (if (seq params)
    (apply str base-uri "?" (query-str params))
    base-uri))


(defn search-system-publisher
  "Returns a Publisher that produces a Bundle per page of resources.

  Use `resource-processor` to transform the pages to individual resources. Use
  `search-system` if you simply want to fetch all resources."
  {:arglists '([client params])}
  [{:keys [base-uri http-client]} params]
  (reify Flow$Publisher
    (subscribe [_ subscriber]
      (->> (search-system-uri base-uri params)
           (impl/paging-subscription subscriber http-client)
           (flow/on-subscribe! subscriber)))))


(defn search-system
  "Returns a CompletableFuture that completes with all resource in case of
  success or completes exceptionally with an anomaly in case of an error."
  ([client]
   (search-system client {}))
  ([client params]
   (let [src (search-system-publisher client params)
         pro (resource-processor)
         dst (flow/collect pro)]
     (flow/subscribe! src pro)
     dst)))


(defn spit
  "Returns a CompletableFuture that completes with a vector of all filenames
  written of all resources the `publisher` produces."
  [dir publisher]
  (let [future (ac/future)]
    (flow/subscribe! publisher (impl/spitter dir future))
    future))
