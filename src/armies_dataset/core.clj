(ns armies-dataset.core
  "Code to build the wars dataset"
  (:require [clj-http.client :as client]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [net.cgrand.enlive-html :as html])
  (:use [clojure.pprint :only [pprint]])
  (:import [java.io StringReader PushbackReader]
           [com.google.common.base CharMatcher]))

(def *src-urls*
  {:1000-1499 "http://en.wikipedia.org/wiki/List_of_wars_1000%E2%80%931499"
   :1500-1799 "http://en.wikipedia.org/wiki/List_of_wars_1500%E2%80%931799"
   :1800-1899 "http://en.wikipedia.org/wiki/List_of_wars_1800%E2%80%9399"
   :1900-1944 "http://en.wikipedia.org/wiki/List_of_wars_1900%E2%80%9344"
   :1945-1989 "http://en.wikipedia.org/wiki/List_of_wars_1945%E2%80%9389"
   :1990-2002 "http://en.wikipedia.org/wiki/List_of_wars_1990%E2%80%932002"
   :2003-2010 "http://en.wikipedia.org/wiki/List_of_wars_2003%E2%80%9310"
   :2011-present "http://en.wikipedia.org/wiki/List_of_wars_2011%E2%80%93present"})

(def *corpus-file* "wars_1000_to_now.corpus")

(def *stats-file* "wars_1000_to_now.clj")

(def *per-decade-file* "participants_by_decade.clj")

(def *top-players* "top_players.clj")

(def *active-armies-per-decade* "active_armies_per_decade.clj")

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

(defn participants-histogram
  "Generate a file containing
   start-date and participants in the war"
  []
  (sort-by
   :start
   (filter
    (fn [x]
      (and (-> x :participants empty? not)
           (-> x :start nil? not)))
    (map
     (fn [{start   :start
          end     :end     
          name    :name    
          winners :winners 
          losers  :losers  }]
       {:start (try (Integer/parseInt start)
                    (catch NumberFormatException e nil))
        :participants (map
                       #(.trimFrom CharMatcher/WHITESPACE %)
                       (concat winners losers))})
     (read
      (PushbackReader.
       (io/reader *stats-file*)))))))

(defn participants-histogram-by-decade
  "A list of participants by the decade"
  []
  (let [histogram (sort-by
                   first
                   (map
                    (fn [[decade participants]]
                      [decade
                       (reverse
                        (sort-by second participants))])
                    (reduce
                     (fn [acc {start :start participants :participants}]
                       (let [decade (* 10 (quot start 10))
                             participants-freq (frequencies participants)]
                         (merge-with
                          (fn [x y]
                            (merge-with + x y))
                          acc
                          {decade participants-freq})))
                     {}
                     (participants-histogram))))]
    (pprint histogram (io/writer *per-decade-file*))))

(defn important-entities-list
  []
  (let [per-decade (read
                    (PushbackReader.
                     (io/reader *per-decade-file*)))
        participants (map
                      first
                      (sort-by
                       second
                       (filter
                        (fn [[x n]]
                          (>= n 4))
                        (reduce
                         (fn [acc freqs]
                           (merge-with + acc freqs))
                         (map
                          (fn [[decade participants]]
                            (into {} participants))
                          per-decade)))))]
    (pprint
     participants
     (io/writer *top-players*))))

(defn active-armies-by-decade
  []
  (let [top-players (set
                     (read
                      (PushbackReader.
                       (io/reader *top-players*))))

        per-decade-players (read
                            (PushbackReader.
                             (io/reader *per-decade-file*)))]
    (map
     (fn [[decade players]]
       (let [top10 (set
                    (take
                     10
                     (reverse
                      (sort-by
                       second
                       players))))
             in-top (set
                     (filter
                      (fn [[x n]]
                        (some #{x} top-players))
                      players))]
         [decade (sort-by second (clojure.set/union top10 in-top))]))
     (pprint per-decade-players (io/writer *active-armies-per-decade*)))))
