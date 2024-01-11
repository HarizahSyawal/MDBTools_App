package com.example.mdbtools_app;

class Constants {

    // values have to be globally unique
    static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    static final String INTENT_ACTION_DISCONNECT = BuildConfig.APPLICATION_ID + ".Disconnect";
    static final String NOTIFICATION_CHANNEL = BuildConfig.APPLICATION_ID + ".Channel";
    static final String INTENT_CLASS_MAIN_ACTIVITY = BuildConfig.APPLICATION_ID + ".MainActivity";

    // values have to be unique within each app
    static final int NOTIFY_MANAGER_START_FOREGROUND_SERVICE = 1001;

    // Command to machine for checking status
    static final String REQUEST_API_VERSION = "A1\r\n";
    static final String REQUEST_MACHINE_ID = "A2\r\n";
    static final String RESTART = "A3\r\n";
    static final String REQUEST_GPU_VERSION = "A4\r\n";
    static final String REQUEST_CPU_VERSION = "A5'b'\r\n";
    static final String REQUEST_EXTENDED_CPU_VERSION = "A1\r\n";
    static final String GET_CPU_SCREEN_MESSAGE = "C1\r\n";
    static final String DATA_TRANSFER_HEADER = "A1\r\n";
    static final String DATA_TRANSFER_BLOCK = "A1\r\n";
    static final String GET_CPU_STATUS = "A1\r\n";
    static final String GET_MACHINE_LOCKING_STATUS = "A1\r\n";
    static final String GET_12_BTNLED_STATUS = "A1\r\n";
    static final String GET_CUP_SENSOR_STATUS = "A1\r\n";
    static final String GET_DAILY_SALES_DATA = "A1\r\n";
    static final String SET_SELECTION_PARAM = "A1\r\n";
    static final String GET_SELECTION_PARAM = "A1\r\n";


    // Command to machine for operation
    static final String START_SELECTION = "S1\r\n"; // S1 [sel_num] [ck]
    static final String QUERY_SELECTION_STATUS = "S2\r\n"; // S2 [sel_num] [ck]
    static final String SELECTION_ALREADY_PAID = "S3\r\n"; // S3 [sel_num][price16 LSB-MSB][ck]
    static final String SEND_BUTTON_PRESS = "S4\r\n"; // S4 [btn] [ck]
    static final String DISPLAY_TEXT_ONSCREEN = "S6\r\n"; //S6 [howLong_sec] [msgLen16 LSB-MSB][UTF8_0][UTF8_N]
    static final String ENABLE_SELECTION = "S7\r\n"; //S7 [selNum] [0x01][ck]
    static final String DISABLE_SELECTION = "S7\r\n"; //S7 [selNum] [0x00][ck]
    static final String GET_SELECTION_AVAILABILITY = "C2\r\n"; //C2 [selNum][ck]
    static final String GET_SELECTION_PRICE = "C3\r\n"; //C3 [selNum(bytes)][priceLen(ASCII Char)]
    static final String GET_SELECTION_NAME = "C5\r\n"; // C5[selNum][ck]




    private Constants() {}
}
