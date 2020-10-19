(ns todo.utils)

(defn- move-from-lower [v i j]
  (let [low-index (min i j)
        high-index (max i j)
        start (subvec v 0 low-index)
        mid (subvec v (inc low-index) (inc high-index))
        end (subvec v (inc high-index) (count v))]
    (vec (concat start mid [(nth v low-index)] end))))

(defn- move-from-higher [v i j]
  (let [low-index (min i j)
        high-index (max i j)
        start (subvec v 0 low-index)
        mid (subvec v low-index high-index)
        end (subvec v (inc high-index) (count v))]
    (vec (concat start [(nth v high-index)] mid end))))

(defn move
  "Returns a vector in which an element in the vector v the element in position
  m has moved to position n"
  [v from to]
  (letfn [(clamp [i]
            (max 0 (min (dec (count v)) i)))]
    (let [from (clamp (or from 0))
          to (clamp (or to 0))
          from-lower (< from to)]
      (if from-lower
        (move-from-lower v from to)
        (move-from-higher v from to)))))
