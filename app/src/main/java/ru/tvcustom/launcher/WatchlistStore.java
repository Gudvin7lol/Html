package ru.tvcustom.launcher;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.UUID;

/**
 * User-maintained watchlist. It never claims to read playback history from OTT apps.
 * Poster URIs are from Android's Storage Access Framework with user-granted access.
 */
public final class WatchlistStore {
    private static final String KEY="watchlist_v1";
    private static final int MAX_ENTRIES=30;
    private WatchlistStore(){}

    public static class Entry {
        public final String id;
        public String title;
        public String packageName;
        public String posterUri;
        public int progress;
        Entry(String id,String title,String packageName,int progress,String posterUri){
            this.id=id;
            this.title=title;
            this.packageName=packageName;
            this.progress=progress;
            this.posterUri=posterUri;
        }
    }
    private static String clean(String s,int max){
        if(s==null)return "";
        s=s.trim().replace('\n',' ').replace('\r',' ');
        return s.length()>max?s.substring(0,max):s;
    }
    public static ArrayList<Entry> load(SharedPreferences prefs){
        ArrayList<Entry> out=new ArrayList<>();
        try{
            JSONArray data=new JSONArray(prefs.getString(KEY,"[]"));
            for(int i=0;i<data.length()&&out.size()<MAX_ENTRIES;i++){
                JSONObject o=data.optJSONObject(i);
                if(o==null)continue;
                String id=clean(o.optString("id",""),80);
                String title=clean(o.optString("title",""),80);
                String pkg=clean(o.optString("package",""),200);
                String uri=clean(o.optString("poster",""),1024);
                if(id.length()==0||title.length()==0||!pkg.matches("[A-Za-z][A-Za-z0-9_.]{1,199}"))continue;
                out.add(new Entry(id,title,pkg,Math.max(0,Math.min(100,o.optInt("progress",0))),uri));
            }
        }catch(JSONException ignored){}
        return out;
    }
    public static void save(SharedPreferences prefs,ArrayList<Entry> entries){
        JSONArray array=new JSONArray();
        for(Entry item:entries){
            if(array.length()>=MAX_ENTRIES)break;
            JSONObject o=new JSONObject();
            try{
                o.put("id",clean(item.id,80));
                o.put("title",clean(item.title,80));
                o.put("package",clean(item.packageName,200));
                o.put("poster",clean(item.posterUri,1024));
                o.put("progress",Math.max(0,Math.min(100,item.progress)));
                array.put(o);
            }catch(JSONException ignored){}
        }
        prefs.edit().putString(KEY,array.toString()).apply();
    }
    public static Entry add(SharedPreferences prefs,String title,String pkg){
        ArrayList<Entry> entries=load(prefs);
        if(entries.size()>=MAX_ENTRIES)return null;
        Entry e=new Entry(UUID.randomUUID().toString(),clean(title,80),clean(pkg,200),0,"");
        if(e.title.isEmpty()||!e.packageName.matches("[A-Za-z][A-Za-z0-9_.]{1,199}"))return null;
        entries.add(0,e);
        save(prefs,entries);
        return e;
    }
    public static boolean update(SharedPreferences prefs,Entry entry){
        ArrayList<Entry> entries=load(prefs);
        for(int i=0;i<entries.size();i++){
            if(entries.get(i).id.equals(entry.id)){
                entries.set(i,entry);save(prefs,entries);return true;
            }
        }
        return false;
    }
    public static void remove(SharedPreferences prefs,String id){
        ArrayList<Entry> entries=load(prefs);
        entries.removeIf(e->e.id.equals(id));
        save(prefs,entries);
    }
}
