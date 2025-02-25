(ns blaze.byte-string
  (:require
    [cheshire.generate :refer [JSONable]])
  (:import
    [com.google.common.io BaseEncoding]
    [com.google.protobuf ByteString]
    [com.fasterxml.jackson.core JsonGenerator]
    [java.io Writer]
    [java.nio ByteBuffer]
    [java.nio.charset Charset])
  (:refer-clojure :exclude [concat empty nth subs < <= > >=]))


(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defn byte-string? [x]
  (instance? ByteString x))


(def empty
  ByteString/EMPTY)


(defn from-byte-array
  ([bs]
   (ByteString/copyFrom ^bytes bs))
  ([bs offset size]
   (ByteString/copyFrom ^bytes bs offset size)))


(defn from-utf8-string [s]
  (ByteString/copyFromUtf8 s))


(defn from-string [s charset]
  (ByteString/copyFrom ^String s ^Charset charset))


(defn from-byte-buffer
  "Returns the remaining bytes from `byte-buffer` as byte string."
  ([byte-buffer]
   (ByteString/copyFrom ^ByteBuffer byte-buffer))
  ([byte-buffer size]
   (ByteString/copyFrom ^ByteBuffer byte-buffer ^int size)))


(defn from-hex [s]
  (ByteString/copyFrom (.decode (BaseEncoding/base16) s)))


(defn nth [bs index]
  (.byteAt ^ByteString bs index))


(defn size
  {:inline (fn [bs] `(.size ~(vary-meta bs assoc :tag `ByteString)))}
  [bs]
  (.size ^ByteString bs))


(defn subs
  ([bs start]
   (.substring ^ByteString bs start))
  ([bs start end]
   (.substring ^ByteString bs start end)))


(defn concat [a b]
  (.concat ^ByteString a b))


(defn starts-with?
  "Test whether `byte-string` starts with `prefix`."
  [byte-string prefix]
  (.startsWith ^ByteString byte-string prefix))


(defn < [a b]
  (neg? (.compare (ByteString/unsignedLexicographicalComparator) a b)))


(defn <= [a b]
  (clojure.core/<= (.compare (ByteString/unsignedLexicographicalComparator) a b) 0))


(defn > [a b]
  (pos? (.compare (ByteString/unsignedLexicographicalComparator) a b)))


(defn >= [a b]
  (clojure.core/>= (.compare (ByteString/unsignedLexicographicalComparator) a b) 0))


(defn hex [bs]
  (.encode (BaseEncoding/base16) (.toByteArray ^ByteString bs)))


(defn to-byte-array [bs]
  (.toByteArray ^ByteString bs))


(defn to-string [bs charset]
  (.toString ^ByteString bs ^Charset charset))


(defn as-read-only-byte-buffer [bs]
  (.asReadOnlyByteBuffer ^ByteString bs))


(extend-protocol JSONable
  ByteString
  (to-json [byte-string jg]
    (.writeBinary ^JsonGenerator jg (.toByteArray byte-string))))


(defmethod print-method ByteString [^ByteString bs ^Writer w]
  (.write w "#blaze/byte-string\"")
  (.write w ^String (hex bs))
  (.write w "\""))


(defmethod print-dup ByteString [^ByteString bs ^Writer w]
  (.write w "#=(com.google.protobuf.ByteString/copyFrom ")
  (print-dup (.toByteArray bs) w)
  (.write w ")"))

