package com.demo.api.controller;

import com.demo.api.model.ApiResponse;
import com.demo.api.model.Product;
import com.demo.api.service.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // GET /api/v1/products
    @GetMapping
    public ResponseEntity<ApiResponse<List<Product>>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(ApiResponse.ok("Products fetched successfully", products));
    }

    // GET /api/v1/products/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Product>> getProductById(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(product -> ResponseEntity.ok(ApiResponse.ok("Product found", product)))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("Product not found with id: " + id)));
    }

    // GET /api/v1/products/category/{category}
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<Product>>> getByCategory(@PathVariable String category) {
        List<Product> products = productService.getProductsByCategory(category);
        if (products.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("No products found in category: " + category));
        }
        return ResponseEntity.ok(ApiResponse.ok("Products in category '" + category + "' fetched successfully", products));
    }
}
