package com.example.mdbtools_app;

import java.nio.charset.StandardCharsets;

final class laRheaCommands {
    private static int chk;
    private static int totalChk;
    private static String finalMsg = "";
    private static String command = "";
    private static int lsbMsb;

    static String startSelection(int itemSelection){
//        String chk2;
//        command = Constants.START_SELECTION+itemSelection;
//        chk2 = TextUtil.calculateCHK2(command);
        command = Constants.START_SELECTION;
        chk = TextUtil.calculateCHK(command);
        totalChk = itemSelection + chk;
        finalMsg = command+ itemSelection + TextUtil.convertToHex(totalChk).trim();
        // '#' 'S' '1' 0x05{item selection} , 0xAC{total checksum in hex}
        return finalMsg;
    }

    static String querySelectionStatus(){
        command = Constants.QUERY_SELECTION_STATUS;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk);

        // '#' 'S' '1' 0x05{item selection} , 0xAC{total checksum in hex}
        return finalMsg;
    }

    static String alreadyPaidSelection(int itemSelection, int itemPrice){
        String finalItemPrice = TextUtil.convertToHex(itemPrice);
        lsbMsb = Integer.parseInt(TextUtil.getLSBMSB(Integer.parseInt(finalItemPrice)));
        command = Constants.SELECTION_ALREADY_PAID+itemSelection+finalItemPrice+lsbMsb;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+itemSelection + TextUtil.convertToHex(chk).trim();

        // # S 3 [sel_num] [price16 LSB-MSB] [ck]
        return finalMsg;
    }

    static String restartCoffeeMachine(){
        command = Constants.RESTART;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // #, A, 3,0x97
        return finalMsg;
    }

    static String startSelectionExtended(int itemSelection, int sugarLevel, int toppingIndex){
        String version = "0x01";
        command = Constants.START_SELECTION_EXTENDED+version+itemSelection+sugarLevel+toppingIndex;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // # S 8 [version] [sel_num] [sugar_level] [topping_index] [ck]
        return finalMsg;
    }

    static String getSelectionAvailability(){
        command = Constants.GET_SELECTION_AVAILABILITY;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // # C 2 [ck]  {# , C, 8, 0x9E}
        return finalMsg;
    }

    static String getSelectionPrice(int itemSelection){
        command = Constants.GET_SELECTION_PRICE+itemSelection;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // # C 3 [selNum] [ck]  {# , C, 8, 0x9E}
        return finalMsg;
    }

    static String getSelectionName(int itemSelection){
        command = Constants.GET_SELECTION_NAME+itemSelection;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // # C 5 [selNum] [ck]  {# , C, 8, 0x9E}
        return finalMsg;
    }

    static String setCustomSelection(int paramID,int level){
        lsbMsb = Integer.parseInt(TextUtil.getLSBMSB(level));
        command = Constants.SET_SELECTION_PARAM+paramID+"0x10"+TextUtil.convertToHex(level)+lsbMsb;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // # P 'S' [selNum] [paramID] [0x10] [value16 LSB-MSB] [ck]
        return finalMsg;
    }

    static String getCustomSelection(int itemSelection){
        command = Constants.GET_SELECTION_PARAM+itemSelection;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // # P 'G' [selNum] [paramID] [ck]
        return finalMsg;
    }

    static String displayTextOnScreen(int duration, String message){
        lsbMsb = Integer.parseInt(TextUtil.getLSBMSB(message.length()));
        command = Constants.DISPLAY_TEXT_ONSCREEN+duration+TextUtil.convertToHex(message.length())+lsbMsb+message+message.getBytes(StandardCharsets.UTF_8);
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // # S 6 [howLong_sec] [msgLen16 LSB-MSB] [UTF8_0] … [UTF8_n] [ck]
        return finalMsg;
    }

    static String getDailySalesData(){
        command = Constants.GET_DAILY_SALES_DATA;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // # C 8 [ck] {# , C, 8, 0x9E}
        return finalMsg;
    }

    static String enableSelection(int itemSelection){
        String enableSelection = "0x01";
        command = Constants.ENABLE_SELECTION+itemSelection+enableSelection;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // # S 7 [selNum] [enabled] [ck]
        return finalMsg;
    }

    static String disableSelection(int itemSelection){
        String disableSelection = "0x00";
        command = Constants.DISABLE_SELECTION+itemSelection+disableSelection;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // # S 7 [selNum] [disabled] [ck]
        return finalMsg;
    }

    static String sendPressButton(int button){
        command = Constants.SEND_BUTTON_PRESS+button;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // # S 4 [btn] [ck]
        return finalMsg;
    }

    static String lockCoffeeMachine(){
        String lockStatus = "0x01";
        command = Constants.LOCK_UNLOCK_MACHINE+lockStatus;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // # S 5 [desired_lock_status] [ck]
        return finalMsg;
    }

    static String unlockCoffeeMachine(){
        String lockStatus = "0x00";
        command = Constants.LOCK_UNLOCK_MACHINE+lockStatus;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // # S 5 [desired_lock_status] [ck]
        return finalMsg;
    }

    static String getMachineLockingStatus(){
        command = Constants.GET_MACHINE_LOCKING_STATUS;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // # C 9 [ck]
        return finalMsg;
    }

    static String getCupSensorStatus(){
        command = Constants.GET_CUP_SENSOR_STATUS;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // # C 6 [ck]  {# , C, 8}
        return finalMsg;
    }

    static String getCpuStatus(){
        command = Constants.GET_CPU_STATUS;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // # C 7 [ck]  {# , C, 7}
        return finalMsg;
    }

    static String getCpuScreenMessage(){
        command = Constants.GET_CPU_SCREEN_MESSAGE;
        chk = TextUtil.calculateCHK(command);
        finalMsg = command+TextUtil.convertToHex(chk).trim();

        // # C 1 [ck]
        return finalMsg;
    }

}
