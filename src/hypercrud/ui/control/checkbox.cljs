(ns hypercrud.ui.control.checkbox)


(defn checkbox* [r change! & [props]]
  [:input {:type "checkbox"
           :checked @r
           :on-change change!}])
