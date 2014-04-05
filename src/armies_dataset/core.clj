(ns armies-dataset.core
  "Code to build the wars dataset"
  (:require [clj-http.client :as client]
            [clojure.java.io :as io])
  (:use [clojure.pprint :only [pprint]]))

(def *src-urls* {:1000-1499 "http://en.wikipedia.org/wiki/List_of_wars_1000%E2%80%931499"
                 :1500-1799 "http://en.wikipedia.org/wiki/List_of_wars_1500%E2%80%931799"
                 :1800-1899 "http://en.wikipedia.org/wiki/List_of_wars_1800%E2%80%9399"
                 :1900-1944 "http://en.wikipedia.org/wiki/List_of_wars_1900%E2%80%9344"
                 :1945-1989 "http://en.wikipedia.org/wiki/List_of_wars_1990%E2%80%932002"
                 :1990-2002 "http://en.wikipedia.org/wiki/List_of_wars_2003%E2%80%9310"
                 :2003-2010 "http://en.wikipedia.org/wiki/List_of_wars_2011%E2%80%93present"})

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
   (io/writer "wars_1000_to_now.corpus")))

