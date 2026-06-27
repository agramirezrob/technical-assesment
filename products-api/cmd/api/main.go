package main

import (
	"encoding/json"
	"log"
	"net/http"
	"os"
	"sort"
	"strings"
)

type product struct {
	ProductID     string `json:"productId"`
	Name          string `json:"name"`
	SKU           string `json:"sku"`
	Category      string `json:"category"`
	TaxCategory   string `json:"taxCategory"`
	UnitOfMeasure string `json:"unitOfMeasure"`
}

var products = map[string]product{
	"PRD-001": {"PRD-001", "Gaseosa 600ml", "GAS-600-PET", "BEBIDAS", "GRAVADO", "UNIT"},
	"PRD-002": {"PRD-002", "Agua potable 600ml", "AGU-600", "BEBIDAS", "EXENTO", "UNIT"},
	"PRD-003": {"PRD-003", "Jugo de naranja 1L", "JUG-1L", "BEBIDAS", "GRAVADO", "UNIT"},
	"PRD-004": {"PRD-004", "Galletas integrales", "GAL-INT", "ALIMENTOS", "REDUCIDO", "PACK"},
	"PRD-005": {"PRD-005", "Arroz premium 1kg", "ARR-1K", "ALIMENTOS", "EXENTO", "BAG"},
	"PRD-006": {"PRD-006", "Cafe tostado 250g", "CAF-250", "ALIMENTOS", "GRAVADO", "PACK"},
	"PRD-007": {"PRD-007", "Detergente 1L", "DET-1L", "LIMPIEZA", "GRAVADO", "UNIT"},
	"PRD-008": {"PRD-008", "Leche descremada 1L", "LEC-1L", "LACTEOS", "REDUCIDO", "UNIT"},
	"PRD-009": {"PRD-009", "Pan integral", "PAN-INT", "ALIMENTOS", "EXENTO", "UNIT"},
	"PRD-010": {"PRD-010", "Medicamento basico", "MED-100", "FARMACIA", "EXENTO", "UNIT"},
}

func main() {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(http.StatusOK) })
	mux.HandleFunc("/products", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/products" {
			http.NotFound(w, r)
			return
		}
		values := make([]product, 0, len(products))
		for _, value := range products {
			values = append(values, value)
		}
		sort.Slice(values, func(i, j int) bool { return values[i].ProductID < values[j].ProductID })
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(values)
	})
	mux.HandleFunc("/products/", func(w http.ResponseWriter, r *http.Request) {
		id := strings.TrimPrefix(r.URL.Path, "/products/")
		value, found := products[id]
		if !found {
			http.Error(w, `{"message":"product not found"}`, http.StatusNotFound)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(value)
	})
	log.Fatal(http.ListenAndServe(":"+os.Getenv("PORT"), mux))
}
