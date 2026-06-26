package main

import (
    "encoding/json"
    "log"
    "net/http"
    "os"
    "strings"
)

type product struct {
    ProductID string `json:"productId"`
    Name string `json:"name"`
    SKU string `json:"sku"`
    Category string `json:"category"`
    TaxCategory string `json:"taxCategory"`
    UnitOfMeasure string `json:"unitOfMeasure"`
}

var products = map[string]product{
    "product-001": {"product-001", "Bebida cola 500ml", "COL-500", "BEBIDAS", "GRAVADO", "UNIT"},
    "product-002": {"product-002", "Agua mineral 600ml", "AGU-600", "BEBIDAS", "EXENTO", "UNIT"},
    "product-003": {"product-003", "Jugo naranja 1L", "JUG-1L", "BEBIDAS", "GRAVADO", "UNIT"},
    "product-004": {"product-004", "Galletas integrales", "GAL-INT", "ALIMENTOS", "REDUCIDO", "PACK"},
    "product-005": {"product-005", "Arroz premium 1kg", "ARR-1K", "ALIMENTOS", "EXENTO", "BAG"},
    "product-006": {"product-006", "Café tostado 250g", "CAF-250", "ALIMENTOS", "GRAVADO", "PACK"},
    "product-007": {"product-007", "Detergente 1L", "DET-1L", "LIMPIEZA", "GRAVADO", "UNIT"},
    "product-008": {"product-008", "Papel higiénico x4", "PAP-4", "HOGAR", "GRAVADO", "PACK"},
    "product-009": {"product-009", "Leche descremada 1L", "LEC-1L", "LACTEOS", "REDUCIDO", "UNIT"},
    "product-010": {"product-010", "Pan integral", "PAN-INT", "ALIMENTOS", "EXENTO", "UNIT"},
}

func main() {
    mux := http.NewServeMux()
    mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(http.StatusOK) })
    mux.HandleFunc("/products/", func(w http.ResponseWriter, r *http.Request) {
        id := strings.TrimPrefix(r.URL.Path, "/products/")
        value, found := products[id]
        if !found { http.Error(w, `{"message":"product not found"}`, http.StatusNotFound); return }
        w.Header().Set("Content-Type", "application/json")
        json.NewEncoder(w).Encode(value)
    })
    log.Fatal(http.ListenAndServe(":"+os.Getenv("PORT"), mux))
}
