(ns armies-dataset.core
  "Code to build the wars dataset"
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [net.cgrand.enlive-html :as html])
  (:use [clojure.pprint :only [pprint]])
  (:import [java.io StringReader PushbackReader]))

(def *src-urls*
  {:1000-1499 "http://en.wikipedia.org/wiki/List_of_wars_1000%E2%80%931499"
   :1500-1799 "http://en.wikipedia.org/wiki/List_of_wars_1500%E2%80%931799"
   :1800-1899 "http://en.wikipedia.org/wiki/List_of_wars_1800%E2%80%9399"
   :1900-1944 "http://en.wikipedia.org/wiki/List_of_wars_1900%E2%80%9344"
   :1945-1989 "http://en.wikipedia.org/wiki/List_of_wars_1990%E2%80%932002"
   :1990-2002 "http://en.wikipedia.org/wiki/List_of_wars_2003%E2%80%9310"
   :2003-2010 "http://en.wikipedia.org/wiki/List_of_wars_2011%E2%80%93present"})

(def *corpus-file* "wars_1000_to_now.corpus")

(def *stats-file* "wars_1000_to_now.clj")

(defn download-corpus
  []
  (pprint
   (into
    {}
    (map
     (fn [[time url]]
       [time (-> url
                 client/get
                 :body)])
     *src-urls*))
   (io/writer *corpus-file*)))

(defn process-cell
  "Code to format a wikipedia cell
   and process it"
  [a-cell]
  (filter
   (fn [x]
     (not= "" x))
   (map
    (fn [x]
      (string/replace
       (nth
        (re-find #"(\[.*\])?(.*)" x)
        2)
       #"\[.*\]|\(.*\)"
       ""))
    (string/split-lines a-cell))))

(defn extract-tables
  [body]
  ;; two rests used to ignore
  ;; table heading rows
  (flatten
   (map
    (fn [a-row]
      (let [elements (map
                      (fn [[start end name winners losers]]
                        {:start (first start)
                         :end (first end)
                         :name (first name)
                         :winners (map string/trim winners)
                         :losers (map string/trim losers)})
                      (partition
                       5
                       5
                       (map
                        #(-> % html/text process-cell)
                        (html/select a-row [:td]))))]
        elements))
    (rest
     (rest
      (html/select
       (html/html-resource
        (StringReader. body))
       [:table.wikitable :tr]))))))

(defn process-corpus
  []
  (clojure.pprint/pprint
   (reduce
    concat
    []
    (map
     (fn [[_ x]]
       (extract-tables x))
     (read
      (PushbackReader.
       (io/reader *corpus-file*)))))
   (io/writer *stats-file*)))
