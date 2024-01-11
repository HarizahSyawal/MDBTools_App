package com.example.mdbtools_app.Model;

public class Product {
    private int id;
    private String name;
    private String price;
    private int imageResource;

    public Product(int id, String name, String price, int imageResource) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.imageResource = imageResource;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPrice() {
        return price;
    }

    public int getImageResource() {
        return imageResource;
    }
}


