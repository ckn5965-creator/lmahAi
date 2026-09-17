package com.imah.app;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class MainActivity extends Activity {
    private LinearLayout chat;
    private EditText input;
    private final ArrayList<String> rules = new ArrayList<>();
    private final ArrayList<JSONObject> history = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private android.content.SharedPreferences prefs;
    private static final String SERVER = "http://10.0.2.2:3000";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("imah", MODE_PRIVATE);
        loadRules();
        buildUi();
        addAi("Xin chào! Mình là Imah. Bạn có thể hỏi mình, yêu cầu viết code, tìm thông tin hoặc tạo ảnh.");
    }

    private void loadRules() {
        String saved = prefs.getString("rules", "");
        if (saved.isEmpty()) {
            rules.add("Trả lời bằng tiếng Việt.");
            rules.add("Nói chuyện thân thiện, rõ ràng.");
            rules.add("Nếu không chắc thì nói rõ, không bịa.");
        } else for (String x : saved.split("\\n")) if (!x.trim().isEmpty()) rules.add(x.trim());
    }

    private void saveRules() {
        StringBuilder s = new StringBuilder();
        for (String r : rules) { if (s.length() > 0) s.append("\n"); s.append(r); }
        prefs.edit().putString("rules", s.toString()).apply();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(18,18,18));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setBackgroundColor(Color.rgb(30,30,36));
        TextView title = new TextView(this); title.setText("🤖 Imah"); title.setTextColor(Color.WHITE); title.setTextSize(21); title.setGravity(Gravity.CENTER_VERTICAL); title.setPadding(18,0,8,0);
        top.addView(title, new LinearLayout.LayoutParams(0,64,1));
        Button rulesBtn = new Button(this); rulesBtn.setText("⚙"); rulesBtn.setOnClickListener(v -> showRules()); top.addView(rulesBtn,new LinearLayout.LayoutParams(64,64)); root.addView(top);
        ScrollView scroll = new ScrollView(this); chat = new LinearLayout(this); chat.setOrientation(LinearLayout.VERTICAL); chat.setPadding(14,14,14,14); scroll.addView(chat); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bar = new LinearLayout(this); bar.setPadding(8,6,8,6);
        input = new EditText(this); input.setHint("Nhắn cho Imah..."); input.setHintTextColor(Color.GRAY); input.setTextColor(Color.WHITE); input.setSingleLine(false); input.setMaxLines(4);
        Button img = new Button(this); img.setText("🖼"); img.setOnClickListener(v -> createImage());
        Button send = new Button(this); send.setText("Gửi"); send.setOnClickListener(v -> sendMessage());
        bar.addView(input,new LinearLayout.LayoutParams(0,-2,1)); bar.addView(img,new LinearLayout.LayoutParams(58,-2)); bar.addView(send,new LinearLayout.LayoutParams(82,-2)); root.addView(bar); setContentView(root);
    }

    private void sendMessage() {
        String q=input.getText().toString().trim(); if(q.isEmpty()) return;
        String lower=q.toLowerCase(Locale.ROOT);
        if(lower.startsWith("luật:")||lower.startsWith("luat:")||lower.startsWith("hãy nhớ luật:")||lower.startsWith("hay nho luat:")){
            String r=q.substring(q.indexOf(":")+1).trim(); if(!r.isEmpty()){rules.add(r);saveRules();input.setText("");addUser(q);addAi("Đã lưu luật mới: "+r);return;}
        }
        input.setText(""); addUser(q);
        try{JSONObject h=new JSONObject();h.put("role","user");h.put("content",q);history.add(h);}catch(Exception ignored){}
        TextView thinking=addAi("Imah đang suy nghĩ…");
        executor.execute(()->{String ans;try{ans=callChat(q);}catch(Exception e){ans="Lỗi kết nối AI: "+e.getMessage();}final String result=ans;runOnUiThread(()->{chat.removeView(thinking);addAi(result);try{JSONObject h=new JSONObject();h.put("role","assistant");h.put("content",result);history.add(h);}catch(Exception ignored){}});});
    }

    private String callChat(String q)throws Exception{
        URL url=new URL(SERVER+"/v1/chat"); HttpURLConnection c=(HttpURLConnection)url.openConnection(); c.setRequestMethod("POST"); c.setConnectTimeout(15000);c.setReadTimeout(90000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
        JSONArray rs=new JSONArray();for(String r:rules)rs.put(r);JSONArray hs=new JSONArray();int start=Math.max(0,history.size()-30);for(int i=start;i<history.size();i++)hs.put(history.get(i));
        JSONObject body=new JSONObject();body.put("message",q);body.put("rules",rs);body.put("history",hs);try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}
        int code=c.getResponseCode();InputStream is=code>=200&&code<300?c.getInputStream():c.getErrorStream();String text=readAll(is);if(code<200||code>=300)throw new IOException("HTTP "+code+": "+text);return new JSONObject(text).optString("answer","AI không trả lời.");
    }

    private void createImage(){String prompt=input.getText().toString().trim();if(prompt.isEmpty()){Toast.makeText(this,"Hãy nhập mô tả ảnh trước.",Toast.LENGTH_SHORT).show();return;}input.setText("");addUser("🎨 Tạo ảnh: "+prompt);TextView thinking=addAi("Đang tạo ảnh…");executor.execute(()->{try{URL url=new URL(SERVER+"/v1/image");HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(180000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");JSONObject body=new JSONObject();body.put("prompt",prompt);try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes(StandardCharsets.UTF_8));}int code=c.getResponseCode();InputStream is=code>=200&&code<300?c.getInputStream():c.getErrorStream();String text=readAll(is);if(code<200||code>=300)throw new IOException("HTTP "+code+": "+text);byte[] bytes=android.util.Base64.decode(new JSONObject(text).getString("image"),android.util.Base64.DEFAULT);File dir=new File(getExternalFilesDir(null),"images");dir.mkdirs();File f=new File(dir,"imah_"+System.currentTimeMillis()+".png");try(FileOutputStream out=new FileOutputStream(f)){out.write(bytes);}final String path=f.getAbsolutePath();runOnUiThread(()->{chat.removeView(thinking);addAiImage(path);});}catch(Exception e){final String msg="Không tạo được ảnh: "+e.getMessage();runOnUiThread(()->{chat.removeView(thinking);addAi(msg);});}});}

    private String readAll(InputStream is)throws Exception{if(is==null)return "";BufferedReader br=new BufferedReader(new InputStreamReader(is,StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();String line;while((line=br.readLine())!=null)s.append(line);br.close();return s.toString();}
    private TextView addAi(String s){TextView t=new TextView(this);t.setText("Imah: "+s);t.setTextColor(Color.WHITE);t.setTextSize(16);t.setPadding(14,12,14,12);t.setBackgroundColor(Color.rgb(35,35,42));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,6,0,6);chat.addView(t,p);return t;}
    private void addUser(String s){TextView t=new TextView(this);t.setText("Bạn: "+s);t.setTextColor(Color.WHITE);t.setTextSize(16);t.setPadding(14,12,14,12);t.setBackgroundColor(Color.rgb(45,55,70));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,6,0,6);chat.addView(t,p);}
    private void addAiImage(String path){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(14,12,14,12);box.setBackgroundColor(Color.rgb(35,35,42));TextView label=new TextView(this);label.setText("Imah: Ảnh đã tạo");label.setTextColor(Color.WHITE);label.setTextSize(16);box.addView(label);ImageView im=new ImageView(this);im.setImageBitmap(BitmapFactory.decodeFile(path));im.setAdjustViewBounds(true);box.addView(im,new LinearLayout.LayoutParams(-1,500));TextView saved=new TextView(this);saved.setText("Đã lưu trong thư mục dữ liệu của Imah.");saved.setTextColor(Color.LTGRAY);box.addView(saved);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,6,0,6);chat.addView(box,p);}
    private void showRules(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(20,5,20,5);TextView hint=new TextView(this);hint.setText("Mỗi dòng là một luật. Bạn cũng có thể nói trong chat: “Luật: …”");hint.setTextColor(Color.LTGRAY);box.addView(hint);EditText r=new EditText(this);r.setText(String.join("\n",rules));r.setTextColor(Color.WHITE);r.setMinLines(10);box.addView(r);new AlertDialog.Builder(this).setTitle("⚙️ Luật của Imah").setView(box).setPositiveButton("Lưu",(d,w)->{rules.clear();for(String x:r.getText().toString().split("\\n"))if(!x.trim().isEmpty())rules.add(x.trim());saveRules();}).setNegativeButton("Hủy",null).show();}
    @Override protected void onDestroy(){super.onDestroy();executor.shutdownNow();}
}
