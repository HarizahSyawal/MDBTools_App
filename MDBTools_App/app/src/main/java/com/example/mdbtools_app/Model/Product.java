package com.example.mdbtools_app.Model;

public class Product {
    private int id;
    private String name;
    private String price;
    private int imageResource;
    private boolean isVisible;
    private String  category;

    public Product(int id, String name, String price, int imageResource, boolean isVisible, String category) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.imageResource = imageResource;
        this.isVisible = isVisible;
        this.category = category;
    }

    public boolean isVisible() {
        return isVisible;
    }

    public void setVisible(boolean visible) {
        isVisible = visible;
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
    public String getCategory(){return category;}
}


