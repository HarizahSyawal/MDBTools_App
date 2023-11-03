package com.example.mdbtools_app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;

import com.example.mdbtools_app.Adapter.ProductAdapter;
import com.example.mdbtools_app.Model.Product;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.os.Bundle;
import android.os.IBinder;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;
import android.widget.Toast;
import com.example.mdbtools_app.Adapter.ProductAdapter;
import com.example.mdbtools_app.Model.Product;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.util.ArrayList;
import java.util.List;

public class VMCScreen extends AppCompatActivity implements ProductAdapter.OnItemClickListener{

    private RecyclerView recyclerView;
    private ProductAdapter productAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vmcscreen);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        productAdapter = new ProductAdapter(this, generateSampleProductList(), this); // Pass this as the listener
        recyclerView.setAdapter(productAdapter);
    }

    private List<Product> generateSampleProductList() {
        List<Product> products = new ArrayList<>();
        products.add(new Product("Pocari Sweat", "$9.99", R.drawable.product1));
        products.add(new Product("Oronamin C", "$19.99", R.drawable.product2));
        // Add more products as needed
        return products;
    }

    @Override
    public void onItemClick(Product product) {

    }
}
