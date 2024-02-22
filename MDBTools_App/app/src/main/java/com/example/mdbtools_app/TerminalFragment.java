package com.example.mdbtools_app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.mdbtools_app.Adapter.ProductAdapter;
import com.example.mdbtools_app.Model.Product;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener,ProductAdapter.OnItemClickListener {

    enum Connected {False, Pending, True}

    private static final String TAG = "TERMINAL FRAGMENT";
    private final BroadcastReceiver broadcastReceiver;
    private int deviceId, portNum, baudRate;
    private UsbSerialPort usbSerialPort;
    private SerialService service;
    private TextView receiveText;
    private TextView sendText;
    private TextUtil.HexWatcher hexWatcher;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private String newline = "";
    private RecyclerView recyclerView;
    private ProductAdapter productAdapter;
    private int itemSelection;
    private String itemPrice;
    private String itemNum;
    private boolean mIsTransactionProcessing = false;
    private boolean mIsDispensingComplete = false;
    private boolean mIsSelectionDone = false;
    private boolean hideItemSelection = false;
    private String extractCde;

    public TerminalFragment() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Constants.INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    Boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    connect(granted);
                }
            }
        };
    }
    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceId = getArguments().getInt("device");
        portNum = getArguments().getInt("port");
        baudRate = getArguments().getInt("baud");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if (service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try {
            getActivity().unbindService(this);
        } catch (Exception ignored) {
        }
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().registerReceiver(broadcastReceiver, new IntentFilter(Constants.INTENT_ACTION_GRANT_USB));
        if (initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(broadcastReceiver);
        super.onPause();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if (initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        productAdapter = new ProductAdapter(getContext(), generateSampleProductList(), this);
        recyclerView.setAdapter(productAdapter);
        updateProductListVisibility(hideItemSelection);

        return view;
    }

    private void updateProductListVisibility(boolean hideItemSelection) {
        List<Product> productList = generateSampleProductList();

        // Apply the condition to hide items
        for (Product product : productList) {
            if (hideItemSelection && "Coffee".equals(product.getCategory())) {
                product.setVisible(false);
            }
        }

        productAdapter.setProducts(productList);
        productAdapter.notifyDataSetChanged();
    }

    private List<Product> generateSampleProductList() {
        List<Product> products = new ArrayList<>();
        products.add(new Product(1, "Espresso", "4", R.drawable.mocha, true, "Coffee"));
        products.add(new Product(2, "Lungo", "5", R.drawable.product1,true, "Coffee"));
        products.add(new Product(3, "Short White Coffee", "8", R.drawable.product2,true, "Coffee"));
        products.add(new Product(4, "Cappucino", "5", R.drawable.product1,true, "Coffee"));
        products.add(new Product(5, "Milk", "5", R.drawable.hotwater,true, "NonCoffee"));
        products.add(new Product(6, "Latte", "5", R.drawable.product1,true, "NonCoffee"));
        products.add(new Product(7, "Mocha", "6", R.drawable.product2,true, "NonCoffee"));
        products.add(new Product(8, "Chocolate", "6", R.drawable.product1,true, "Coffee"));
        products.add(new Product(9, "ChocoMilk", "4.5", R.drawable.product2,true, "Coffee"));
        products.add(new Product(10, "Coffee", "4", R.drawable.product1,true, "Coffee"));
        products.add(new Product(11, "Americano", "5", R.drawable.product2,true, "Coffee"));
        products.add(new Product(12, "Hot Water", "1", R.drawable.hotwater,true, "NonCoffee"));

        // Add more products as needed
        return products;
    }

    @Override
    public void onItemClick(Product product) throws IOException, InterruptedException {
        itemSelection = product.getId();
        itemPrice = product.getPrice();

        send(laRheaCommands.startSelection(itemSelection));
//        send(laRheaCommands.startSelectionExtended(itemSelection, 3, 1));
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.clear) {
            receiveText.setText("");
            return true;
        } else if (id == R.id.showCoffeeSelection) {
            updateProductListVisibility(false);
            return true;
        }
        else if (id == R.id.getSelectionAvail) {
            send(laRheaCommands.getSelectionAvailability());
            return true;
        } else if (id == R.id.sendRestart) {
            send(laRheaCommands.restartCoffeeMachine());
            return true;
        }
        else if (id == R.id.sendExtSelection) {
            send(laRheaCommands.startSelectionExtended(itemSelection,3,0));
            return true;
        }
        else if (id == R.id.getCpuScreenMessages) {
//            try {
//                Thread.sleep(5000); // Sleep for 5 second
//                send(laRheaCommands.getCpuScreenMessage());
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            send(laRheaCommands.getCpuScreenMessage());
            return true;
        } else if (id == R.id.sendPayment) {
            send(laRheaCommands.alreadyPaidSelection(itemSelection, Integer.parseInt("0")));
            return true;
        }
        else if (id == R.id.getCpuStatus) {
            send(laRheaCommands.getCpuStatus());
            return true;
        }
        else if (id == R.id.sendStatusChecking) {
            try {
                  send(laRheaCommands.querySelectionStatus());
            } catch (Exception e) {
                status(e.getMessage());
            }
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        connect(null);
    }

    private void connect(Boolean permissionGranted) {
        UsbDevice device = null;
        UsbManager usbManager = (UsbManager) getActivity().getSystemService(Context.USB_SERVICE);
        for (UsbDevice v : usbManager.getDeviceList().values())
            if (v.getDeviceId() == deviceId)
                device = v;
        if (device == null) {
            status("connection failed: device not found");
            return;
        }
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        if (driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device);
        }
        if (driver == null) {
            status("connection failed: no driver for device");
            return;
        }
        if (driver.getPorts().size() < portNum) {
            status("connection failed: not enough ports at device");
            return;
        }
        usbSerialPort = driver.getPorts().get(portNum);
        UsbDeviceConnection usbConnection = usbManager.openDevice(driver.getDevice());
        if (usbConnection == null && permissionGranted == null && !usbManager.hasPermission(driver.getDevice())) {
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_MUTABLE : 0;
            PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(getActivity(), 0, new Intent(Constants.INTENT_ACTION_GRANT_USB), flags);
            usbManager.requestPermission(driver.getDevice(), usbPermissionIntent);
            return;
        }
        if (usbConnection == null) {
            if (!usbManager.hasPermission(driver.getDevice()))
                status("connection failed: permission denied");
            else
                status("connection failed: open failed");
            return;
        }

        connected = Connected.Pending;
        try {
            usbSerialPort.open(usbConnection);
            try {
                usbSerialPort.setParameters(baudRate, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            } catch (UnsupportedOperationException e) {
                status("Setting serial parameters failed: " + e.getMessage());
            }
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), usbConnection, usbSerialPort);
            service.connect(socket);
            // usb connect is not asynchronous. connect-success and connect-error are returned immediately from socket.connect
            // for consistency to bluetooth/bluetooth-LE app use same SerialListener and SerialService classes
            onSerialConnect();
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
        usbSerialPort = null;
    }

    void send(String message) {
        if (connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String commandMsg = message.replace(" ", "");
            String finalCommand = TextUtil.hexToAscii(commandMsg.substring(0,6));
            byte [] data = TextUtil.fromHexString(message);

            receiveText.append(newline+"SEND :"+ "["+finalCommand+"]" + "\r" +   message   +"  Len:"+data.length+"\n");
            Log.d(TAG,"SEND MESSAGES :   "+ "["+finalCommand+"]" + "\r" +   message   +" Len:"+data.length+"\n");
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(ArrayDeque<byte[]> datas) {

        for (byte[] data : datas) {
            String msg = TextUtil.bytesToHex(data);

            if (msg.length() >= 6) {
                String finalCommand = TextUtil.hexToAscii(msg.substring(0, 6));
                receiveText.append("\n" + "Receive : " + "[" + finalCommand + "]" + "\r" + "[" + msg + "]" + "\r" + " Len:" + data.length + "\n");
                handleReceivedMessage(msg);
            }
            else {
                Log.d(TAG, "RECEIVE MESSAGES : ");
            }
        }
    }

    public void handleReceivedMessage(String data) {
        Log.d(TAG, "[{}] handleReceivedMessage: [{}]" + data);

        if (null == data || data.equalsIgnoreCase("00")) {
            receiveText.append("command is null");
            Log.d(TAG, "laRhea Command is null !!!");
        } else {
            String command = TextUtil.hexToAscii(data.substring(0,6));
            String extractedMsg = data;
            if(command.equalsIgnoreCase("#A1")){
                receiveText.append("Receiced API VERSION" + "["+extractedMsg.substring(6, 10)+"]");
                Log.d(TAG, "Receiced API VERSION" + "["+extractedMsg.substring(6, 10)+"]");
            }
            else if (command.equalsIgnoreCase("#S1")){
                itemNum = data.substring(7, 9);
                if (itemNum.equalsIgnoreCase("00")){
                    send(laRheaCommands.getCpuScreenMessage());
                    receiveText.append("Received Invalid Selection Command");
                    Log.d(TAG, "Received Invalid Selection Command");
                } else if (command.equals("Not Available")) {

                } else {
                    Log.d(TAG, "Selected Item : "+itemNum);
                    mIsSelectionDone = true;
                    send(laRheaCommands.querySelectionStatus());
                }
                    receiveText.append("Item selected : " + command);
                    mIsSelectionDone = true;
                    send(laRheaCommands.querySelectionStatus());
            }
            else if (command.equalsIgnoreCase("#S2")){
                Log.d(TAG,"Received check query selection status : " + command);

                if (mIsSelectionDone) {
                    handleSelectionStatus(data);
                }
                else {
                    command = data.substring(6, 8);
                    receiveText.append(newline + "Please Choose Your Desired item from mini menu "+command);
                    Log.d(TAG,"Received check query selection status : " + command);
                }
            }
            else if (command.equalsIgnoreCase("#S3")){
                Log.d(TAG, "Received Selection already paid command" + command);
                itemNum = data.substring(3,5);
                if (command.equalsIgnoreCase("#S3")) {
                    receiveText.append("Received Selection already paid command "+ command);
                    Log.d(TAG, "Received Selection already paid command "+ command);
                    try {
                         Thread.sleep(3000); // Sleep for 3 second
                         send(laRheaCommands.querySelectionStatus());
                      } catch (InterruptedException e) {
                           e.printStackTrace();
                      }
                }
                else if(itemNum.equalsIgnoreCase("00")) {
                    receiveText.append("Invalid selection item"+itemNum);
                    Log.d(TAG, "Invalid selection item"+itemNum);
                }
            }
            else if (command.equalsIgnoreCase("#S4")){
                receiveText.append("Send button press command receive"+command);
                Log.d(TAG, "Send button press command receive"+command);
//                handlePressingButton(command);
            }
            else if (command.equalsIgnoreCase("#S5")){
                if (command.equalsIgnoreCase("00")){
                    receiveText.append("Unlock the machine command receive"+command);
                    Log.d(TAG, "Unlock the machine command receive");
                }else {
                    receiveText.append("lock the machine command receive" + command);
                    Log.d(TAG, "lock the machine command receive");
                }
            }
            else if (command.equalsIgnoreCase("#S6")){
                if (command.equalsIgnoreCase("00")){
                receiveText.append("Received display text on screen command"+command);
                    Log.d(TAG, "Received display text on screen command");
                }else {
                    receiveText.append("Error displaying text on screen"+command);
                    Log.d(TAG, "Error displaying text on screen");
                }
            }
            else if (command.equalsIgnoreCase("#S7")){
                if (command.equalsIgnoreCase("01")){
                    receiveText.append("Received Enabled Selection"+command);
                    Log.d(TAG, "Received Enabled Selection");
                }else {
                    receiveText.append("Received Disabled Selection"+command);
                    Log.d(TAG, "Received Disabled Selection");
                }
            }
            else if (command.equalsIgnoreCase("#S8")){
                receiveText.append("Received extended selection command"+command);
                Log.d(TAG, "Received extended selection command"+command);
                mIsSelectionDone = true;
                send(laRheaCommands.querySelectionStatus());
            }
            else if (command.equalsIgnoreCase("#C1")){
                receiveText.append("Received CPU Screen Message Command " + command);
                Log.d(TAG, "Received CPU Screen Message Command " + command);
                handleVendFailure(data);
            }
            else if (command.equalsIgnoreCase("#C2")){
                receiveText.append("Received get selection availability");
                Log.d(TAG, "Received get selection availability" + command);
                handleItemAvailability(data);
            }
            else if (command.equalsIgnoreCase("#C3")){
                receiveText.append("Received Get Selection Price"+command);
                Log.d(TAG, "Received Get Selection Price");
            }
            else if (command.equalsIgnoreCase("#C4")){
                receiveText.append("Received Get 12 BTN-LED Status"+command);
                Log.d(TAG, "Received Get 12 BTN-LED Status");
            }
            else if (command.equalsIgnoreCase("#C5")){
                receiveText.append("Received Get Selection Name"+command);
                Log.d(TAG, "Received Get Selection Name");
            }
            else if (command.equalsIgnoreCase("#C6")){
                receiveText.append("Received Get Cup Sensor Status"+command);
                Log.d(TAG, "Received Get Cup Sensor Status");
            }
            else if (command.equalsIgnoreCase("#C7")){
                receiveText.append("Received Get Cpu Status"+command);
                Log.d(TAG, "Received Get Cpu Status " + command);
            }
            else if (command.equalsIgnoreCase("#C8")){
                receiveText.append("Received Get Daily Sales Data"+command);
                Log.d(TAG, "Received Get Daily Sales Data");
            }
            else if (command.equalsIgnoreCase("#C9")){
                receiveText.append("Received Get Machine Locking Status"+command);
                Log.d(TAG, "Received Get Machine Locking Status");
            }
            else if (command.equalsIgnoreCase("#PS")){
                receiveText.append("Received Set Custom Selection Param"+command);
                Log.d(TAG, "Received Set Custom Selection Param");
            }
            else {
//                LOGGER.warn("[{}] Received ?? Command: {}. Please check !!!", TAG, data);
            }
        }
    }

    private void handleItemAvailability(String command) {
        String extractMsg = command.substring(6, 18);
        String hexToBinary = TextUtil.hexToBinary(extractMsg);
        String itemAvailability = TextUtil.getItemAvailalbility(hexToBinary);

        Log.d(TAG, "Selection(s) available :" + itemAvailability);
    }

    private void handleSelectionStatus(String data) {

//        if (mIsTransactionProcessing && !mIsSelectionDone) {
//            send(laRheaCommands.querySelectionStatus());
//            return;
//        } else if(!mIsDispensingComplete && !mIsSelectionDone){
//            send(laRheaCommands.querySelectionStatus());
//        }

        String command = data.substring(6, 8);

        if (command.equalsIgnoreCase("01") && mIsSelectionDone){
            receiveText.append(newline + "Waiting for payment \n" + command);
            Log.d(TAG, "Waiting for payment" + "Status :"+ command) ;
//            send(laRheaCommands.alreadyPaidSelection(itemSelection, Integer.parseInt(itemPrice)));
        }
        else if (command.equalsIgnoreCase("02")){
            Log.d(TAG, "Delivering in progress "+ command);
            receiveText.append(newline + "delivering in progress "+command);
        }
        else if (command.equalsIgnoreCase("03")){
            Log.d(TAG, "Finished KO Status " + command);
            receiveText.append(newline + "Finished KO, Selection haven't pay "+command);
        }
        else if (command.equalsIgnoreCase("04")){
            Log.d(TAG, "Finished OK Status "+command);
            receiveText.append(newline + "Finished OK "+command);
            mIsSelectionDone = false;

            send(laRheaCommands.getCpuScreenMessage());
        }
        else if (command.equalsIgnoreCase("05")){
            Log.d(TAG, "Delivering in progress "+command);
            receiveText.append(newline + "delivering in progress"+command);

            mIsDispensingComplete = true;
        }
    }

    private void handleVendFailure(String message){
        if (!mIsTransactionProcessing){
//            sendPaymentVoid();
        }
        extractCde = TextUtil.hexToAscii(message);
        if (extractCde.contains("product reserve REFILL COFFE") || extractCde.contains("product reserve")
        || extractCde.contains("product reserve REFILL")){
//            receiveText.append(" REFILL COFFEE BEANS");
//            updateProductListVisibility(true);
        }
        else if (extractCde.contains("place your cup")){
//            send(laRheaCommands.getCpuScreenMessage());
        }
        else if (message.length() > 100){
            updateProductListVisibility(false);
            String errorCde = message.substring(105,106);
            String errorMsg = extractCde.substring(21,26).trim();

            if (errorMsg.equalsIgnoreCase("OFF 6")) {

                if (errorCde.equalsIgnoreCase("C")){
                    Log.d(TAG, "Excessive air breaker filling time; there may be no hydraulic power, the pressure may be \n" +
                            "insufficient, or some obstruction may slow the proper flow of water (filter screen, narrowed \n" +
                            "or clogged drain pipes)");
                } else if (errorCde.equalsIgnoreCase("D")) {
                    Log.d(TAG, "water entered the hydraulic circuit without dispensing drinks; there may be a leak in \n" +
                            "the power circuit");
                } else if (errorCde.equalsIgnoreCase("G")) {
                    Log.d(TAG, "During the first installation, there was an error in filling water into the device");
                }
            }
            else if (errorMsg.contains("OFF 7")) {
                if (errorCde.contains("A")){
                    Log.d(TAG, "Time inclusion pump hydraulic contour exceeded limit value");
                }
                else if (errorCde.contains("C")){
                    Log.d(TAG, "During the brewing phase, the chamber moved downwards, beyond the safety limits, due to pressure");
                } else if (errorCde.contains("D")) {
                    Log.d(TAG, "the volumetric counter did not detect pulses within three seconds");
                } else if (errorCde.contains("R")) {
                    Log.d(TAG, "error at the water recycling stage");
                }
            }
            else if (errorMsg.contains("OFF 8")) {
                Log.d(TAG, "Coffee maker issue");
                if (errorCde.contains("A")){
                    Log.d(TAG, "block motor error due to missing or erroneous power supply, rotation detection error");
                } else if (errorCde.contains("B")) {
                    Log.d(TAG, "the device does not detect the presence of a block");
                }
            }
            else if (errorMsg.contains("OFF 9") || extractCde.contains("REFILL COFFEE")) {
                updateProductListVisibility(true);
                Log.d(TAG, "Coffee beans issue : indicates that the amount of ground coffee is less than required or missing");
                receiveText.append("Coffee beans issue : indicates that the amount of ground coffee is less than required or missing");
            }
            else if (errorMsg.contains("OFF 10")) {
                Log.d(TAG, "the stored data is inappropriate (reading or writing error) or the overall functioning of the device is not as expected");
            }
            else if (errorMsg.contains("OFF 14")) {
                errorCde.substring(4,5);
                if (errorCde.contains("V")){
                    Log.d(TAG, "Hydraulic circuit : the water is not filled");
                }
            }
            else if (errorMsg.contains("OFF 17")) {
                Log.d(TAG, "keypads issue");
                errorCde.substring(4,5);
                if (errorCde.contains("A")){
                    Log.d(TAG, "the button is defined as if it is always pressed");
                }
            }
            else if (errorMsg.contains("OFF 24")) {
                errorCde.substring(4,5);
                if (errorCde.contains("A")){
                    Log.d(TAG, "The effective voltage value of 24 V DC exceeds the permissible value");
                }
                else if (errorCde.contains("V")){
                    Log.d(TAG, "the measured 24 VDC voltage is below the permissible limit or missing");
                }
            }
            else if (errorMsg.contains("OFF 31")) {
                Log.d(TAG, "Water coffee espresso issue");
                errorCde.substring(4,5);
                if (errorCde.contains("A")){
                    Log.d(TAG, "the boiler water temperature exceeds the programmed value");
                }
                else if (errorCde.contains("B")){
                    Log.d(TAG, "the water does not reach the set temperature");
                }
                else if (errorCde.contains("C")){
                    Log.d(TAG, "the temperature sensor is interrupted or its electrical connector is disconnected");
                }
                else if (errorCde.contains("D")){
                    Log.d(TAG, "temperature not reaches programmed values V acceptable time limits");
                }
                else if (errorCde.contains("H")){
                    Log.d(TAG, "lack of power to the induction sensor; clicson has tripped, no current is supplied from the \n" +
                            "circuit, wiring is disconnected or out of use");
                }
            }
            else if (errorCde.contains("42")){
                Log.d(TAG, "indicates the need to service the coffee machine due to the number of expressos \n" +
                        "dispensed;");
            }
            else if (errorCde.contains("43")){
                Log.d(TAG, "the number of used pods in the container has reached the maximum limit");
            }
            else if (errorCde.contains("77")){
                Log.d(TAG, "The clock function does not work properly");
            }
        }
        else {
            send(laRheaCommands.getCpuScreenMessage());
        }
    }

    void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        Toast.makeText(getActivity(),  "vmc connected", Toast.LENGTH_SHORT).show();

        connected = Connected.True;

        send(laRheaCommands.getCpuScreenMessage());
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
        ArrayDeque<byte[]> datas = new ArrayDeque<>();
        datas.add(data);
        receive(datas);
        Log.d(TAG, "RECEIVING A MESSAGES : "+ TextUtil.bytesToHex(data));
    }

    public void onSerialRead(ArrayDeque<byte[]> datas) {
        receive(datas);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        Toast.makeText(getActivity(),  "connection lost :"+e.getMessage(), Toast.LENGTH_SHORT).show();
        disconnect();
    }
}
