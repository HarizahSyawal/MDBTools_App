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
    private int lockButtonStop = 1;
    private int unlockButtonStop = 2;
    private StringBuilder mVMMessageSB = new StringBuilder();
    private boolean mIsTransactionProcessing = false;
    private boolean mIsDispensingComplete = false;
    private boolean mIsCustomSelectionDone = false;
    private int sendByteCount = 0;
    private byte[] writeBuffer = new byte[8192];
    private byte[] outputBuffer = new byte[8192];

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

        sendText = view.findViewById(R.id.send_text);

        View sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));

        recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        productAdapter = new ProductAdapter(getContext(), generateSampleProductList(), this);
        recyclerView.setAdapter(productAdapter);

        return view;
    }

    private List<Product> generateSampleProductList() {
        List<Product> products = new ArrayList<>();
        products.add(new Product(1, "Coffee Latte", "7", R.drawable.product1));
        products.add(new Product(2, "Cappucino", "7", R.drawable.product1));
        products.add(new Product(3, "Americano", "3", R.drawable.product2));
        products.add(new Product(4, "White Coffee", "6", R.drawable.product1));
        // Add more products as needed
        return products;
    }

    @Override
    public void onItemClick(Product product) throws IOException, InterruptedException {
        itemSelection = product.getId();
        itemPrice = product.getPrice();

        send(laRheaCommands.startSelection(itemSelection));
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
        } else if (id == R.id.paymentButton) {
            send(laRheaCommands.requestAPIVersion());
//            send(laRheaCommands.alreadyPaidSelection(itemSelection, Integer.parseInt(itemPrice)));
            return true;
        } else if (id == R.id.sendBreak) {
            try {
//                send(laRheaCommands.getCpuScreenMessage());
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
            byte [] data = TextUtil.convertToByteArray(message);

            receiveText.append(newline+"SEND :"+ "["+finalCommand+"]" + "\r" +   message   +"  Len:"+data.length+"\n");
            Log.d(TAG,"SEND MESSAGES :    "+ "["+finalCommand+"]" + "\r" +   message   +" Len:"+data.length+"\n");
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

//    private void receive(ArrayDeque<byte[]> datas) {
//
//        char STX = '\u0002'; // Start of Text
//        char ETX = '\u0003'; // End of Text
//
//        for (byte[] data : datas) {
//            String msg = new String(data);
//            String messageWithoutSTXETX = msg.replace(STX, ' ').replace(ETX, ' ').trim();
//            mVMMessageSB.append(messageWithoutSTXETX);
//
//            char lastChar = msg.charAt(msg.length() - 1);
//            if (lastChar == ETX) {
//                handleReceivedMessage(mVMMessageSB.toString());
//                mVMMessageSB = new StringBuilder();
//            }
//        }
//    }

    private void receive(ArrayDeque<byte[]> datas) {

//        for (byte[] data : datas) {
//            String msg = new String(data);
//            handleReceivedMessage(msg);
//            Log.d(TAG,"RECEIVE MESSAGES : "+msg);
//        }

        for (byte[] data : datas) {
            String msg = TextUtil.bytesToHex(data);
            handleReceivedMessage(TextUtil.hexToAscii(msg));
            Log.d(TAG, "RECEIVE MESSAGES : " + TextUtil.hexToAscii(msg));
        }
    }

    public void handleReceivedMessage(String data) {
        Log.d(TAG, "[{}] handleReceivedMessage: [{}]" + data);

        if (null == data) {
            receiveText.append("command is null");
            Log.d(TAG, "laRhea Command is null !!!");
        } else {
            String command = data;
            if (command.equalsIgnoreCase("#C2")){

                receiveText.append("Received get selection availability");
                Log.d(TAG, "Received get selection availability");
            }
            else if (command.equalsIgnoreCase("#S1")){
                receiveText.append("Received Start a selection command");
                Log.d(TAG, "Received Start a selection command");
                command = data.substring(2, 3);
                if (command.equalsIgnoreCase("0")){
                    receiveText.append("Received Invalid Selection Command");
                    Log.d(TAG, "Received Invalid Selection Command");
                }
                else {
                    send(laRheaCommands.querySelectionStatus());
                }
            }
            else if (command.equalsIgnoreCase("#S2")){
                receiveText.append("Received Query Selection Status command");
                Log.d(TAG, "Received Query Selection Status command");
                handleSelectionStatus(data);
            }
            else if (command.equalsIgnoreCase("#S3")){
                command = data.substring(0,2);
                if (command.equalsIgnoreCase(String.valueOf(itemSelection))) {
                    receiveText.append("Received Selection already paid command");
                    Log.d(TAG, "Received Selection already paid command");
                }
                else if(itemSelection == 0) {
                    receiveText.append("Invalid selection item");
                    Log.d(TAG, "Invalid selection item");
                }
            }
            else if (command.equalsIgnoreCase("#S4")){
                receiveText.append("Send button press command receive");
                Log.d(TAG, "Send button press command receive");
                handlePressingButton(command);
            }
            else if (command.equalsIgnoreCase("#S5")){
                command = data.substring(1,2);
                if (command.equalsIgnoreCase("0x00")){
                    receiveText.append("Unlock the machine command receive"+command);
                    Log.d(TAG, "Unlock the machine command receive");
                }else {
                    receiveText.append("lock the machine command receive" + command);
                    Log.d(TAG, "lock the machine command receive");
                }
            }
            else if (command.equalsIgnoreCase("#S6")){
                command = data.substring(1,2);
                if (command.equalsIgnoreCase("0x00")){
                receiveText.append("Received display text on screen command"+command);
                    Log.d(TAG, "Received display text on screen command");
                }else {
                    receiveText.append("Error displaying text on screen"+command);
                    Log.d(TAG, "Error displaying text on screen");
                }
            }
            else if (command.equalsIgnoreCase("#S7")){
                command = data.substring(1,2);
                if (command.equalsIgnoreCase("0x01")){
                    receiveText.append("Received Enabled Selection"+command);
                    Log.d(TAG, "Received Enabled Selection");
                }else {
                    receiveText.append("Received Disabled Selection"+command);
                    Log.d(TAG, "Received Disabled Selection");
                }
            }
            else if (command.equalsIgnoreCase("#S8")){
                receiveText.append("Received extended selection command"+command);
                Log.d(TAG, "Received extended selection command");
                mIsCustomSelectionDone = true;
                send(laRheaCommands.querySelectionStatus());
            }
            else if (command.equalsIgnoreCase("#C1")){
                receiveText.append("Received CPU Screen Message Command"+command);
                Log.d(TAG, "Received CPU Screen Message Command");
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
                Log.d(TAG, "Received Get Cpu Status");
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

    private void handlePressingButton(String command) {
        String button = command.substring(2,3);

        if (command.equalsIgnoreCase(String.valueOf(lockButtonStop))){
            receiveText.append("Received lock button stop"+button);
            Log.d(TAG, "Received lock button stop");
            send(laRheaCommands.querySelectionStatus());
        }
        else if(command.equalsIgnoreCase(String.valueOf(unlockButtonStop))){
            receiveText.append("Received unlock button stop"+button);
            Log.d(TAG, "Received unlock button stop");
            send(laRheaCommands.querySelectionStatus());
        }
//        else if (!mIsTransactionProcessing && mIsDispensingComplete){
//            send(laRheaCommands.querySelectionStatus());
//        }
    }

    private void handleSelectionStatus(String data) {

        if (mIsTransactionProcessing && !mIsCustomSelectionDone) {
            send(laRheaCommands.querySelectionStatus());
            return;
        } else if(mIsDispensingComplete && !mIsCustomSelectionDone){
            send(laRheaCommands.querySelectionStatus());
        }

        String command = data.substring(1,2);

        if (command.equalsIgnoreCase("0x01")){
            receiveText.append("Waiting for payment"+command);
            Log.d(TAG, "Waiting for payment");
            if (mIsTransactionProcessing){
//                            openPaymentOption(actualPrice);
            }
            else {

            }
        }
        else if (command.equalsIgnoreCase("0x02")){
            Log.d(TAG, "Delivering in progress, send lock Stop button command");
            receiveText.append("delivering in progress, send lock Stop button command"+command);

            send(laRheaCommands.sendPressButton(lockButtonStop));
        }
        else if (command.equalsIgnoreCase("0x03")){
            Log.d(TAG, "Finished KO Status");
            receiveText.append("Finished KO"+command);
        }
        else if (command.equalsIgnoreCase("0x04")){
            Log.d(TAG, "Finished OK Status");
            receiveText.append("Finished OK"+command);
        }
        else if (command.equalsIgnoreCase("0x05")){
            Log.d(TAG, "Delivering in progress, send unlock Stop button command");
            receiveText.append("delivering in progress, STOP button is available"+command);

            mIsDispensingComplete = true;
            send(laRheaCommands.sendPressButton(unlockButtonStop));
        }
    }

    private void handleVendFailure(String errorMsg){
        String errorCde = errorMsg.substring(3,4);
        if (!mIsTransactionProcessing){
//            sendPaymentVoid();
        }else {
            if (errorMsg.equalsIgnoreCase("OFF3")) {
                Log.d(TAG, "The sensor monitoring the liquid level of the drip tray has triggered");
            } else if (errorMsg.equalsIgnoreCase("OFF5")) {
                Log.d(TAG, "Systems integrated into the CPU circuit do not function properly");
            }
            else if (errorMsg.equalsIgnoreCase("OFF6")) {

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
            else if (errorMsg.equalsIgnoreCase("OFF7")) {
                if (errorCde.equalsIgnoreCase("A")){
                    Log.d(TAG, "Delivering in progress, send unlock Stop button command");
                }
                else if (errorCde.equalsIgnoreCase("C")){
                    Log.d(TAG, "Delivering in progress, send unlock Stop button command");
                } else if (errorCde.equalsIgnoreCase("D")) {
                    Log.d(TAG, "Delivering in progress, send unlock Stop button command");
                } else if (errorCde.equalsIgnoreCase("R")) {
                    Log.d(TAG, "Delivering in progress, send unlock Stop button command");
                }
            }
            else if (errorMsg.equalsIgnoreCase("OFF8")) {
                if (errorCde.equalsIgnoreCase("A")){
                    Log.d(TAG, "Delivering in progress, send unlock Stop button command");
                } else if (errorCde.equalsIgnoreCase("B")) {
                    Log.d(TAG, "Delivering in progress, send unlock Stop button command");
                }
            }
            else if (errorMsg.equalsIgnoreCase("OFF9")) {

            }
            else if (errorMsg.equalsIgnoreCase("OFF10")) {

            }
            else if (errorMsg.equalsIgnoreCase("OFF14")) {
                errorCde.substring(4,5);
                if (errorCde.equalsIgnoreCase("V")){
                    Log.d(TAG, "Delivering in progress, send unlock Stop button command");
                }
            }
            else if (errorMsg.equalsIgnoreCase("OFF17")) {
                errorCde.substring(4,5);
                if (errorCde.equalsIgnoreCase("A")){
                    Log.d(TAG, "Delivering in progress, send unlock Stop button command");
                }
            }
            else if (errorMsg.equalsIgnoreCase("OFF24")) {
                errorCde.substring(4,5);
                if (errorCde.equalsIgnoreCase("A")){
                    Log.d(TAG, "Delivering in progress, send unlock Stop button command");
                }
                else if (errorCde.equalsIgnoreCase("V")){
                    Log.d(TAG, "Delivering in progress, send unlock Stop button command");
                }
            }
            else if (errorMsg.equalsIgnoreCase("OFF31")) {
                errorCde.substring(4,5);
                if (errorCde.equalsIgnoreCase("A")){
                    Log.d(TAG, "Delivering in progress, send unlock Stop button command");
                }
                else if (errorCde.equalsIgnoreCase("B")){
                    Log.d(TAG, "Delivering in progress, send unlock Stop button command");
                }
                else if (errorCde.equalsIgnoreCase("C")){
                    Log.d(TAG, "Delivering in progress, send unlock Stop button command");
                }
                else if (errorCde.equalsIgnoreCase("D")){
                    Log.d(TAG, "Delivering in progress, send unlock Stop button command");
                }
                else if (errorCde.equalsIgnoreCase("H")){
                    Log.d(TAG, "Delivering in progress, send unlock Stop button command");
                }
            }
            else if (errorCde.equalsIgnoreCase("42")){
                Log.d(TAG, "Delivering in progress, send unlock Stop button command");
            }
            else if (errorCde.equalsIgnoreCase("43")){
                Log.d(TAG, "Delivering in progress, send unlock Stop button command");
            }
            else if (errorCde.equalsIgnoreCase("77")){
                Log.d(TAG, "Delivering in progress, send unlock Stop button command");
            }
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
