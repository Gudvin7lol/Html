package ru.tvcustom.launcher;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Only launcher preferences. No VPN profiles, passwords or account credentials. */
public final class LauncherBackup {
    public static final int SCHEMA=1;
    private LauncherBackup(){}

    private static ArrayList<String> parseIds(String joined){
        ArrayList<String> ids=new ArrayList<>();
        if(joined==null)return ids;
        for(String id:joined.split(",")){
            if(id.matches("[A-Za-z][A-Za-z0-9_.]{0,199}")&&!ids.contains(id)&&ids.size()<100)ids.add(id);
        }
        return ids;
    }
    public static String exportConfig(SharedPreferences p) throws JSONException {
        JSONObject output=new JSONObject();
        output.put("format","xiaomi-tv-custom-settings");
        output.put("schema",SCHEMA);
        JSONArray favorites=new JSONArray();
        ArrayList<String> order=parseIds(p.getString("favorite_order",""));
        Set<String> old=p.getStringSet("favorites",new HashSet<>());
        for(String id:old){
            if(id.matches("[A-Za-z][A-Za-z0-9_.]{0,199}")&&!order.contains(id)&&order.size()<100)order.add(id);
        }
        for(String id:order)favorites.put(id);
        output.put("favorites",favorites);
        JSONArray recent=new JSONArray();
        for(String id:parseIds(p.getString("recent_apps","")))recent.put(id);
        output.put("recent",recent);
        JSONObject options=new JSONObject();
        options.put("motion",p.getBoolean("motion",true));
        options.put("scenic",p.getBoolean("scenic",true));
        options.put("youtube_vpn",p.getBoolean("youtube_vpn",true));
        output.put("options",options);
        // SAF URI permission doesn't migrate across installs, so poster_uri is not backed up.
        return output.toString(2);
    }
    public static int importConfig(String json, SharedPreferences p) throws JSONException {
        if(json==null||json.length()>131072)throw new JSONException("Размер файла больше 128 КБ");
        JSONObject data=new JSONObject(json);
        if(!"xiaomi-tv-custom-settings".equals(data.optString("format"))||
            data.optInt("schema",-1)!=SCHEMA)throw new JSONException("Неверный формат конфигурации");
        JSONArray fav=data.optJSONArray("favorites");
        JSONObject options=data.optJSONObject("options");
        if(fav==null||options==null)throw new JSONException("Не найдены настройки");
        if(fav.length()>100)throw new JSONException("Слишком много избранных");
        ArrayList<String> ids=new ArrayList<>();
        for(int i=0;i<fav.length();i++){
            String id=fav.optString(i,"");
            if(!id.matches("[A-Za-z][A-Za-z0-9_.]{0,199}"))throw new JSONException("Неверное имя пакета");
            if(!ids.contains(id))ids.add(id);
        }
        ArrayList<String> recent=new ArrayList<>();
        JSONArray r=data.optJSONArray("recent");
        if(r!=null){
            for(int i=0;i<Math.min(r.length(),8);i++){
                String id=r.optString(i,"");
                if(id.matches("[A-Za-z][A-Za-z0-9_.]{0,199}")&&!recent.contains(id))recent.add(id);
            }
        }
        boolean motion=options.optBoolean("motion",true);
        boolean scenic=options.optBoolean("scenic",true);
        boolean vpn=options.optBoolean("youtube_vpn",true);
        p.edit()
          .putString("favorite_order",android.text.TextUtils.join(",",ids))
          .putString("recent_apps",android.text.TextUtils.join(",",recent))
          .remove("favorites")
          .putBoolean("motion",motion)
          .putBoolean("scenic",scenic)
          .putBoolean("youtube_vpn",vpn)
          .apply();
        return ids.size();
    }
}
