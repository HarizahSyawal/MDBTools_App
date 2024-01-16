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
    static final String REQUEST_API_VERSION = "#A1";
    static final String REQUEST_MACHINE_ID = "#A2";
    static final String RESTART = "#A3";
    static final String REQUEST_GPU_VERSION = "#A4";
    static final String REQUEST_CPU_VERSION = "#A5";
    static final String REQUEST_EXTENDED_CPU_VERSION = "#A5b";
    static final String GET_CPU_SCREEN_MESSAGE = "#C1";
    static final String DATA_TRANSFER_HEADER = "#X0";
    static final String DATA_TRANSFER_BLOCK = "#X1";
    static final String GET_CPU_STATUS = "#C7";
    static final String GET_MACHINE_LOCKING_STATUS = "#C9";
    static final String GET_12_BTNLED_STATUS = "#C4";
    static final String GET_CUP_SENSOR_STATUS = "#C6";
    static final String GET_DAILY_SALES_DATA = "#C8";
    static final String SET_SELECTION_PARAM = "#PS";
    static final String GET_SELECTION_PARAM = "#PG";


    // Command to machine for operation
    static final String START_SELECTION = "#S1"; // S1 [sel_num] [ck]
    static final String QUERY_SELECTION_STATUS = "#S2"; // S2 [sel_num] [ck]
    static final String SELECTION_ALREADY_PAID = "#S3"; // S3 [sel_num][price16 LSB-MSB][ck]
    static final String SEND_BUTTON_PRESS = "#S4"; // S4 [btn] [ck]
    static final String LOCK_UNLOCK_MACHINE = "#S5"; //
    static final String DISPLAY_TEXT_ONSCREEN = "#S6"; //S6 [howLong_sec] [msgLen16 LSB-MSB][UTF8_0][UTF8_N]
    static final String ENABLE_SELECTION = "#S7"; //S7 [selNum] [0x01][ck]
    static final String DISABLE_SELECTION = "#S7"; //S7 [selNum] [0x00][ck]
    static final String START_SELECTION_EXTENDED = "#S8";
    static final String GET_SELECTION_AVAILABILITY = "#C2"; //C2 [selNum][ck]
    static final String GET_SELECTION_PRICE = "#C3"; //C3 [selNum(bytes)][priceLen(ASCII Char)]
    static final String GET_SELECTION_NAME = "#C5"; // C5[selNum][ck]

    // Custom Selection Parameter
    static final String EV_FRESHMILK = "0x01";
    static final String EV_FRESHMILK_DELAY = "0x02";
    static final String EV_AIR_FRESHMILK = "0x03";
    static final String EV_AIR_FRESHMILK_DELAY = "0x04";
    static final String COFFEE_GROUND_QTY = "0x05";
    static final String COFFEE_WATER_QTY = "0x06";
    static final String FOAM_TYPE = "0x07"; //(value:0 = hot, no foam, 1=hot foam 1... 7=cold foam 3 )


    private Constants() {}
}
