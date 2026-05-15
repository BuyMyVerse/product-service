package com.demo.api.service;

import com.demo.api.model.Product;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Optional;

@Service
public class ProductService {

    private final List<Product> products = new ArrayList<>();

    public ProductService() {
        products.add(new Product(1L, "Laptop Pro 15",      "High-performance laptop with 15\" display",  1299.99, "Electronics", 50));
        products.add(new Product(2L, "Wireless Mouse",     "Ergonomic wireless mouse with 12-month battery", 29.99, "Accessories", 200));
        products.add(new Product(3L, "Mechanical Keyboard","RGB backlit mechanical keyboard",             89.99,  "Accessories", 150));
        products.add(new Product(4L, "4K Monitor",         "27-inch 4K UHD IPS monitor",                 399.99, "Electronics", 75));
        products.add(new Product(5L, "USB-C Hub",          "7-in-1 USB-C multiport adapter",             49.99,  "Accessories", 300));
        products.add(new Product(6L, "Webcam HD",          "1080p HD webcam with built-in microphone",   79.99,  "Electronics", 120));
    }

    public List<Product> getAllProducts() {
        return Collections.unmodifiableList(products);
    }

    public Optional<Product> getProductById(Long id) {
        return products.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst();
    }

    public List<Product> getProductsByCategory(String category) {
        return products.stream()
                .filter(p -> p.getCategory().equalsIgnoreCase(category))
                .toList();
    }
}
