(ns blaze.rest-api.middleware.output
  "JSON/XML serialization middleware."
  (:require
    [blaze.async.comp :as ac]
    [blaze.fhir.spec :as fhir-spec]
    [cheshire.core :as json]
    [clojure.data.xml :as xml]
    [clojure.string :as str]
    [prometheus.alpha :as prom]
    [ring.util.response :as ring]))


(prom/defhistogram generate-duration-seconds
  "FHIR generating latencies in seconds."
  {:namespace "fhir"}
  (take 17 (iterate #(* 2 %) 0.00001))
  "format")


(defn- generate-json [body]
  (with-open [_ (prom/timer generate-duration-seconds "json")]
    (json/generate-string (fhir-spec/unform-json body) {:key-fn name})))


(defn- xml-response?
  [{{:strs [accept]} :headers {format "_format"} :query-params}]
  (let [accept (or format accept)]
    (or (and accept
           (or (str/starts-with? accept "application/fhir+xml")
               (str/starts-with? accept "application/xml")
               (str/starts-with? accept "text/xml")))
        (= "xml" format))))


(defn- generate-xml [body]
  (with-open [_ (prom/timer generate-duration-seconds "xml")]
    (xml/emit-str (fhir-spec/unform-xml body))))


(defn handle-response [request {:keys [body] :as response}]
  (if body
    (if (xml-response? request)
      (-> (update response :body generate-xml)
          (ring/content-type "application/fhir+xml;charset=utf-8"))
      (-> (update response :body generate-json)
          (ring/content-type "application/fhir+json;charset=utf-8")))
    response))


(defn wrap-output
  "Middleware to output resources in JSON or XML."
  [handler]
  (fn [request]
    (-> (handler request)
        (ac/then-apply #(handle-response request %)))))
