package com.example.hagitai;

import android.content.Context;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class ApiKeyLoader {

    public static String getApiKey(Context context) {
        try {
            InputStream inputStream = context.getResources().openRawResource(R.raw.openai_key);
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);
            inputStream.close();

            String json = new String(buffer, StandardCharsets.UTF_8);
            JSONObject jsonObject = new JSONObject(json);
            return jsonObject.getString("api_key");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
