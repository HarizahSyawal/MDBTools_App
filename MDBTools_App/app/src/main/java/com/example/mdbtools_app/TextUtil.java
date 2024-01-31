package com.example.mdbtools_app;

import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.ColorInt;

import java.io.ByteArrayOutputStream;

final class TextUtil {

    @ColorInt static int caretBackground = 0xff666666;

    final static String newline_crlf = "\r\n";
    final static String newline_lf = "\n";

    static byte[] fromHexString(final CharSequence s) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte b = 0;
        int nibble = 0;
        for(int pos = 0; pos<s.length(); pos++) {
            if(nibble==2) {
                buf.write(b);
                nibble = 0;
                b = 0;
            }
            int c = s.charAt(pos);
            if(c>='0' && c<='9') { nibble++; b *= 16; b += c-'0';    }
            if(c>='A' && c<='F') { nibble++; b *= 16; b += c-'A'+10; }
            if(c>='a' && c<='f') { nibble++; b *= 16; b += c-'a'+10; }
        }
        if(nibble>0)
            buf.write(b);
        return buf.toByteArray();
    }

    static String convertToHex(int decimalValue) {
        return String.format("%02X", decimalValue);
//        return String.format("0x%02X", decimalValue);
    }

//    static byte[] convertToByteArray(String input) {
//        String[] parts = input.split("\\s+");
//        byte[] byteArray = new byte[parts.length];
//
//        for (int i = 0; i < parts.length; i++) {
//            byteArray[i] = Byte.parseByte(parts[i]);
//        }
//
//        return byteArray;
//    }

    static byte[] convertToByteArray(String input) {
        String[] parts = input.split("\\s+");
        byte[] byteArray = new byte[parts.length];

        for (int i = 0; i < parts.length; i++) {
            if (i == parts.length - 1) {
                // Convert the last part to a hexadecimal byte
                byteArray[i] = (byte) Integer.parseInt(parts[i], 16);
            } else {
                // Convert other parts to decimal bytes
                byteArray[i] = Byte.parseByte(parts[i]);
            }
        }

        return byteArray;
    }

    static String getLSBMSB(int decimalValue) {
        byte LSB = getLSB(decimalValue);
        byte MSB = getMSB(decimalValue);
        String finalValue = String.valueOf(LSB+""+MSB);

        return finalValue;
    }

    static byte getLSB(int value) {
        return (byte) (value & 0xFF);
    }

    static byte getMSB(int value) {
        return (byte) ((value >> 8) & 0xFF);
    }

    static String toHexString(final byte[] buf) {
        return toHexString(buf, 0, buf.length);
    }

    static String toHexString(final byte[] buf, int begin, int end) {
        StringBuilder sb = new StringBuilder(3*(end-begin));
        toHexString(sb, buf, begin, end);
        return sb.toString();
    }

    static void toHexString(StringBuilder sb, final byte[] buf) {
        toHexString(sb, buf, 0, buf.length);
    }

    static void toHexString(StringBuilder sb, final byte[] buf, int begin, int end) {
        for(int pos=begin; pos<end; pos++) {
            if(sb.length()>0)
                sb.append(' ');
            int c;
            c = (buf[pos]&0xff) / 16;
            if(c >= 10) c += 'A'-10;
            else        c += '0';
            sb.append((char)c);
            c = (buf[pos]&0xff) % 16;
            if(c >= 10) c += 'A'-10;
            else        c += '0';
            sb.append((char)c);
        }
    }

    /**
     * use https://en.wikipedia.org/wiki/Caret_notation to avoid invisible control characters
     */
    static CharSequence toCaretString(CharSequence s, boolean keepNewline) {
        return toCaretString(s, keepNewline, s.length());
    }

    static CharSequence toCaretString(CharSequence s, boolean keepNewline, int length) {
        boolean found = false;
        for (int pos = 0; pos < length; pos++) {
            if (s.charAt(pos) < 32 && (!keepNewline ||s.charAt(pos)!='\n')) {
                found = true;
                break;
            }
        }
        if(!found)
            return s;
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for(int pos=0; pos<length; pos++)
            if (s.charAt(pos) < 32 && (!keepNewline ||s.charAt(pos)!='\n')) {
                sb.append('^');
                sb.append((char)(s.charAt(pos) + 64));
                sb.setSpan(new BackgroundColorSpan(caretBackground), sb.length()-2, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                sb.append(s.charAt(pos));
            }
        return sb;
    }

    static String bytesToHex(byte[] bytes) {
        StringBuilder hexStringBuilder = new StringBuilder();
        for (byte b : bytes) {
            hexStringBuilder.append(String.format("%02X", b));
        }
        return hexStringBuilder.toString();
    }

    static String hexToAscii(String hexString) {
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < hexString.length(); i += 2) {
            String hex = hexString.substring(i, i + 2);
            int decimal = Integer.parseInt(hex, 16);
            output.append((char) decimal);
        }
        return output.toString();
    }

    static class HexWatcher implements TextWatcher {

        private final TextView view;
        private final StringBuilder sb = new StringBuilder();
        private boolean self = false;
        private boolean enabled = false;

        HexWatcher(TextView view) {
            this.view = view;
        }

        void enable(boolean enable) {
            if(enable) {
                view.setInputType(InputType.TYPE_CLASS_TEXT + InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                view.setInputType(InputType.TYPE_CLASS_TEXT + InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            }
            enabled = enable;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if(!enabled || self)
                return;

            sb.delete(0,sb.length());
            int i;
            for(i=0; i<s.length(); i++) {
                char c = s.charAt(i);
                if(c >= '0' && c <= '9') sb.append(c);
                if(c >= 'A' && c <= 'F') sb.append(c);
                if(c >= 'a' && c <= 'f') sb.append((char)(c+'A'-'a'));
            }
            for(i=2; i<sb.length(); i+=3)
                sb.insert(i,' ');
            final String s2 = sb.toString();

            if(!s2.equals(s.toString())) {
                self = true;
                s.replace(0, s.length(), s2);
                self = false;
            }
        }
    }

//    static int calculateCHK(String message) {
//        int sum = 0;
//        for (int i = 0; i < message.length(); i++) {
//            char c = message.charAt(i);
//            int asciiValue = (int) c;
//            sum += asciiValue;
//        }
//        return sum;
//    }
static int calculateCHK(String message) {
    int chk = 0;
    for (int i = 0; i < message.length(); i++) {
        char c = message.charAt(i);
        int asciiValue = (int) c;
        chk += asciiValue;
    }
    return chk;
}
}
