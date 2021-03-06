(ns cljc.repl.util
  (:require [clojure.string :refer [join]]
            [clojure.java.io :refer [reader writer]]
            [clojure.java.shell :as shell])
  (:import [java.io PipedOutputStream PipedInputStream]))

(def ^:private COLORS (Boolean/valueOf (System/getenv "CLJC_REPL_COLORS")))

(defn maybe-colorize [fmt & args]
  (apply format (if COLORS fmt "%s") args))

(defmacro map-stderr [f & body]
  `(let [err# *err*
         os# (PipedOutputStream.)
         is# (PipedInputStream. os#)]
     (.start
      (Thread.
       #(with-open [rdr# (reader is#)]
          (doseq [line# (line-seq rdr#)]
            (binding [*out* err#]
              (println (~f line#)))))))
     (binding [*err* (writer os#)]
       ~@body)))

(defn read-fields [struct fields]
  (loop [fields fields
         result {}]
    (if-let [field (first fields)]
      (recur (rest fields)
             (assoc result field (.readField struct (name field))))
      result)))

(defn redirect-process [proc]
  (let [out *out*
        err *err*]
    (.start
     (Thread.
      #(with-open [rdr (reader (.getInputStream proc))]
         (doseq [line (line-seq rdr)]
           (binding [*out* out]
             (println line))))))
    (.start
     (Thread.
      #(with-open [rdr (reader (.getErrorStream proc))]
         (doseq [line (line-seq rdr)]
           (binding [*out* err]
             (println line))))))
    (.start
     (Thread.
      #(do (.waitFor proc) (System/exit (.exitValue proc)))))))

(defn sh [& cmd]
  (let [cmd (map str (remove nil? (flatten cmd)))
        result (apply shell/sh cmd)]
    (if (= 0 (:exit result))
      (:out result)
      (throw (Error. (str (join " " cmd) "\n"
                          (:err result)))))))

(defn maybe-deref [x]
  (if (instance? clojure.lang.IDeref x) (deref x) x))
