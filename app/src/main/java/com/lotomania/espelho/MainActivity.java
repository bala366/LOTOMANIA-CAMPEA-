package com.lotomania.espelho;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
  static final int PICK=31;
  final int[][] groups={
    {1,3,5,11,17,19,21,23,29,31,37,41,43,55,59,67,73,79,83,97},
    {6,12,14,16,20,22,26,28,32,40,44,52,62,64,66,70,80,86,96,98},
    {9,15,25,33,51,63,65,69,81,93}
  };
  final String[] groupNames={"Primo + Fibonacci","Puro Par","Puro Impar"};
  final int[] quotas={15,10,5};
  ArrayList<int[]> draws=new ArrayList<>();
  ArrayList<Block> ranking=new ArrayList<>();
  int current=0;
  TextView output;
  Button choose, yes, next, go;
  LinearLayout root;
  String sourceName="";
  static class Block {
    int a,b,c,d,miss,last=-1,seq,maxSeq,delay,absent;
    int[] nums;
    String pairLabel(){return String.format(Locale.US,"%02d-%02d / %02d-%02d",a+1,b+1,c+1,d+1);}
  }
  static String fmt(int n){return String.format(Locale.US,"%02d",n%100);}
  static String joined(int[] ns){StringBuilder s=new StringBuilder();for(int n:ns){if(s.length()>0)s.append(' ');s.append(fmt(n));}return s.toString();}
  void status(String text){runOnUiThread(()->output.setText(text));}
  @Override public void onCreate(Bundle b){super.onCreate(b);
    root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(22,25,22,15);
    root.setBackgroundColor(Color.rgb(255,250,225));
    TextView title=new TextView(this);title.setText("LOTO MANIA CAMPEÃO V11");title.setTextSize(24);title.setTextColor(Color.rgb(117,82,0));title.setTypeface(null,1);title.setBackgroundColor(Color.rgb(255,214,45));title.setPadding(12,16,12,16);root.addView(title);
    choose=button("1. ESCOLHER TXT/CSV DO DOWNLOAD");root.addView(choose);
    go=button("2. ANALISAR 137 COMBINACOES");root.addView(go);go.setEnabled(false);
    yes=button("APROVAR ESTAS 20 DEZENAS");root.addView(yes);yes.setEnabled(false);
    next=button("PROXIMA COMBINACAO");root.addView(next);next.setEnabled(false);
    ScrollView scroll=new ScrollView(this);output=new TextView(this);output.setTextSize(15);output.setTextColor(Color.BLACK);output.setPadding(5,12,5,12);output.setText("Selecione o arquivo de resultados da Lotomania.\n\nO Android abre o seletor de documentos. Entre em Downloads e escolha o TXT/CSV.\n\nOs PDFs serao salvos em Download/Lotomania combinações de tiras.");scroll.addView(output);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    choose.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/plain","text/csv","application/csv","application/octet-stream"});startActivityForResult(i,PICK);});
    go.setOnClickListener(v->{go.setEnabled(false);status("Analisando historico...");new Thread(()->{try{compute();runOnUiThread(()->{current=0;yes.setEnabled(true);next.setEnabled(true);showBlock();});}catch(Exception e){status("ERRO: "+e.getMessage());runOnUiThread(()->go.setEnabled(true));}}).start();});
    next.setOnClickListener(v->{current=(current+1)%ranking.size();showBlock();});
    yes.setOnClickListener(v->{yes.setEnabled(false);next.setEnabled(false);new Thread(()->{try{generate(ranking.get(current));}catch(Exception e){status("ERRO AO SALVAR: "+e.toString());}}).start();});
  }
  Button button(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(255,207,38)));b.setTextColor(Color.BLACK);return b;}
  @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=PICK||result!=RESULT_OK||data==null)return;
    Uri uri=data.getData();sourceName=uri.getLastPathSegment();status("Lendo arquivo...");new Thread(()->{try{StringBuilder sb=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri),StandardCharsets.UTF_8))){String l;while((l=br.readLine())!=null)sb.append(l).append('\n');}
      ParseResult r=parse(sb.toString());draws=r.draws;
      status("Arquivo: "+sourceName+"\nConcursos: "+draws.size()+"\nLinhas ignoradas: "+r.rejected+"\nPrimeiro: "+joined(draws.get(0))+"\nUltimo: "+joined(draws.get(draws.size()-1))+"\n\nConfira os dados e toque ANALISAR.");runOnUiThread(()->go.setEnabled(true));
    }catch(Exception e){status("ERRO NA LEITURA: "+e.getMessage());}}).start();
  }
  static class ParseResult{ArrayList<int[]> draws=new ArrayList<>();int rejected;}
  static int[] numbers(String[] vals){if(vals.length!=20)return null;int[] a=new int[20];HashSet<Integer> seen=new HashSet<>();for(int i=0;i<20;i++){try{String t=vals[i].trim().replace("\"","");if(!t.matches("[0-9]{1,3}"))return null;int n=Integer.parseInt(t);if(n==0)n=100;if(n<1||n>100||!seen.add(n))return null;a[i]=n;}catch(Exception e){return null;}}Arrays.sort(a);return a;}
  static String norm(String s){String x=Normalizer.normalize(s.toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","");return x.replaceAll("[^a-z0-9]","");}
  static ParseResult parse(String txt){ParseResult result=new ParseResult();String[] lines=txt.split("\\R");String first="";for(String l:lines)if(!l.trim().isEmpty()){first=l;break;}
    String delim=null;for(String s:new String[]{";",",","\t"})if(first.split(Pattern.quote(s),-1).length>=20){delim=s;break;}
    int[] ballIdx=null;int contestIdx=-1;
    if(delim!=null){String[] h=first.split(Pattern.quote(delim),-1);int[] temp=new int[20];Arrays.fill(temp,-1);for(int i=0;i<h.length;i++){String x=norm(h[i]);Matcher m=Pattern.compile("(?:bola|dezena|numero|num)([0-9]{1,2})").matcher(x);if(m.matches()){int k=Integer.parseInt(m.group(1));if(k>=1&&k<=20)temp[k-1]=i;}if(x.equals("concurso")||x.equals("numeroconcurso")||x.equals("nconcurso"))contestIdx=i;}boolean ok=true;for(int x:temp)if(x<0)ok=false;if(ok)ballIdx=temp;}
    TreeMap<Integer,int[]> numbered=new TreeMap<>();ArrayList<int[]> unnumbered=new ArrayList<>();boolean anyNumbered=false,anyUnnumbered=false;
    for(String line:lines){if(line.trim().isEmpty())continue;int[] draw=null;Integer contest=null;
      if(ballIdx!=null){String[] fields=line.split(Pattern.quote(delim),-1);try{String[] twenty=new String[20];for(int i=0;i<20;i++)twenty[i]=fields[ballIdx[i]];draw=numbers(twenty);if(contestIdx>=0)contest=Integer.parseInt(fields[contestIdx].trim());}catch(Exception ignored){}}
      else {String cleaned=line.replaceAll("\\b[0-9]{1,2}/[0-9]{1,2}/[0-9]{4}\\b|\\b[0-9]{4}-[0-9]{1,2}-[0-9]{1,2}\\b"," ");Matcher m=Pattern.compile("(?<![0-9])[0-9]+(?![0-9])").matcher(cleaned);ArrayList<String> nums=new ArrayList<>();while(m.find())nums.add(m.group());if(nums.size()==20||nums.size()==21){draw=numbers(nums.subList(nums.size()-20,nums.size()).toArray(new String[0]));if(nums.size()==21)contest=Integer.parseInt(nums.get(0));}}
      if(draw==null){result.rejected++;continue;}
      if(contest==null){anyUnnumbered=true;unnumbered.add(draw);}else{anyNumbered=true;int[] old=numbered.put(contest,draw);if(old!=null&&!Arrays.equals(old,draw))throw new IllegalArgumentException("Concurso "+contest+" duplicado com dezenas diferentes");}
    }
    if(anyNumbered&&anyUnnumbered)throw new IllegalArgumentException("Arquivo mistura concursos numerados e nao numerados");
    if(anyNumbered){int firstKey=numbered.firstKey(),lastKey=numbered.lastKey();if(lastKey-firstKey+1!=numbered.size())throw new IllegalArgumentException("Ha concursos faltando entre "+firstKey+" e "+lastKey);result.draws.addAll(numbered.values());}else result.draws=unnumbered;
    if(result.draws.isEmpty())throw new IllegalArgumentException("Nenhum resultado valido; formato aceito: 20 dezenas, concurso + 20 dezenas ou colunas Bola1..Bola20");return result;
  }
  static int[] strip(int i){int base=i<10?0:50,col=i%10+1;int[] a=new int[5];for(int k=0;k<5;k++)a[k]=base+col+10*k;return a;}
  void compute(){ArrayList<int[]> pairs=new ArrayList<>();for(int base:new int[]{0,10})for(int i=base;i<base+9;i++)pairs.add(new int[]{i,i+1});ArrayList<Block> all=new ArrayList<>();
    for(int i=0;i<pairs.size();i++)for(int j=i+1;j<pairs.size();j++){int[] p=pairs.get(i),q=pairs.get(j);if(p[0]==q[0]||p[0]==q[1]||p[1]==q[0]||p[1]==q[1])continue;Block b=new Block();b.a=p[0];b.b=p[1];b.c=q[0];b.d=q[1];b.nums=new int[20];int k=0;for(int x:new int[]{b.a,b.b,b.c,b.d})for(int n:strip(x))b.nums[k++]=n;Arrays.sort(b.nums);all.add(b);}
    if(all.size()!=137)throw new IllegalStateException("Esperados 137 blocos, encontrados "+all.size());
    for(int pos=0;pos<draws.size();pos++){HashSet<Integer> set=new HashSet<>();for(int n:draws.get(pos))set.add(n);for(Block b:all){boolean absent=true;for(int n:b.nums)if(set.contains(n)){absent=false;break;}if(absent){b.miss++;b.seq=b.last==pos-1?b.seq+1:1;b.maxSeq=Math.max(b.maxSeq,b.seq);b.last=pos;}}}
    HashSet<Integer> lastDraw=new HashSet<>();for(int n:draws.get(draws.size()-1))lastDraw.add(n);for(Block b:all){b.delay=b.last<0?draws.size():draws.size()-b.last-1;for(int n:b.nums)if(!lastDraw.contains(n))b.absent++;}
    all.sort((a,b)->{int c=Integer.compare(b.delay,a.delay);if(c==0)c=Integer.compare(b.miss,a.miss);if(c==0)c=Integer.compare(b.absent,a.absent);return c;});ranking=all;
  }
  void showBlock(){Block b=ranking.get(current);output.setText("RANKING POR ATRASO - OPCAO "+(current+1)+" / "+ranking.size()+"\n\nPares: "+b.pairLabel()+"\n\n20 DEZENAS:\n"+joined(b.nums)+"\n\nAtraso de falha inteira: "+b.delay+" concursos\nFalhas historicas: "+b.miss+"\nMaior sequencia: "+b.maxSeq+"\nAusentes no ultimo concurso: "+b.absent+"/20\n\nAprovar estas 20 dezenas ou ver a proxima combinacao?");}
  static class Pick{ArrayList<Integer> chosen=new ArrayList<>();LinkedHashMap<String,ArrayList<Integer>> byGroup=new LinkedHashMap<>();int[] recent=new int[101],historical=new int[101],delay=new int[101];HashSet<Integer> absent=new HashSet<>();}
  Pick select30(Block b){Pick p=new Pick();int window=Math.min(100,draws.size());int[] last=new int[101];Arrays.fill(last,-1);for(int i=0;i<draws.size();i++)for(int n:draws.get(i)){p.historical[n]++;last[n]=i;if(i>=draws.size()-window)p.recent[n]++;}for(int n=1;n<=100;n++)p.delay[n]=last[n]<0?draws.size():draws.size()-last[n]-1;HashSet<Integer> drawn=new HashSet<>();for(int n:draws.get(draws.size()-1))drawn.add(n);for(int n=1;n<=100;n++)if(!drawn.contains(n))p.absent.add(n);
    HashSet<Integer> used=new HashSet<>();for(int n:b.nums)used.add(n);
    Comparator<Integer> cmp=(x,y)->{int c=Boolean.compare(!p.absent.contains(x),!p.absent.contains(y));if(c!=0)return c;c=Integer.compare(p.recent[x],p.recent[y]);if(c!=0)return c;c=Integer.compare(p.historical[x],p.historical[y]);if(c!=0)return c;c=Integer.compare(p.delay[y],p.delay[x]);return c!=0?c:Integer.compare(x,y);};
    for(int g=0;g<3;g++){ArrayList<Integer> pool=new ArrayList<>();for(int n:groups[g])if(!used.contains(n))pool.add(n);pool.sort(cmp);ArrayList<Integer> selected=new ArrayList<>();for(int n:pool){if(selected.size()>=quotas[g])break;selected.add(n);used.add(n);p.chosen.add(n);}p.byGroup.put(groupNames[g],selected);}
    ArrayList<Integer> fallback=new ArrayList<>();for(int n=1;n<=100;n++)if(!used.contains(n))fallback.add(n);fallback.sort(cmp);ArrayList<Integer> extra=new ArrayList<>();for(int n:fallback){if(p.chosen.size()==30)break;p.chosen.add(n);extra.add(n);}p.byGroup.put("Complemento externo",extra);if(p.chosen.size()!=30)throw new IllegalStateException("Nao foi possivel selecionar 30 dezenas");return p;
  }
  void generate(Block b)throws Exception{Pick p=select30(b);TreeSet<Integer> chosen=new TreeSet<>();for(int n:b.nums)chosen.add(n);chosen.addAll(p.chosen);if(chosen.size()!=50)throw new IllegalStateException("50 dezenas nao distintas");TreeSet<Integer> mirror=new TreeSet<>();for(int n=1;n<=100;n++)if(!chosen.contains(n))mirror.add(n);
    StringBuilder report=new StringBuilder();report.append("LOTO MANIA CAMPEÃO V11\nHistorico: ").append(draws.size()).append(" concursos\nFonte: ").append(sourceName).append("\n\n");report.append("20 DEZENAS APROVADAS\nPares: ").append(b.pairLabel()).append("\n").append(joined(b.nums)).append("\nAtraso: ").append(b.delay).append(" | Falhas: ").append(b.miss).append(" | Sequencia maxima: ").append(b.maxSeq).append("\n\n");
    for(Map.Entry<String,ArrayList<Integer>> e:p.byGroup.entrySet()){int[] arr=e.getValue().stream().mapToInt(Integer::intValue).sorted().toArray();report.append(e.getKey()).append(" (").append(arr.length).append("):\n").append(joined(arr)).append("\n\n");}
    report.append("50 DEZENAS SELECIONADAS PARA FALHA:\n").append(joined(chosen.stream().mapToInt(Integer::intValue).toArray())).append("\n\n50 DEZENAS DO ESPELHO:\n").append(joined(mirror.stream().mapToInt(Integer::intValue).toArray())).append("\n\n");int inside=0;for(int n:chosen)if(p.absent.contains(n))inside++;report.append("Dentro das 80 ausentes do ultimo concurso: ").append(inside).append("/50\n\n");report.append("Atrasos e frequencias historicas nao garantem resultados futuros.\n");
    StringBuilder rank=new StringBuilder("Posicao;Pares;Atraso;Falhas;Maior sequencia;Ausentes ultimo;20 dezenas\n");for(int i=0;i<ranking.size();i++){Block r=ranking.get(i);rank.append(i+1).append(';').append(r.pairLabel()).append(';').append(r.delay).append(';').append(r.miss).append(';').append(r.maxSeq).append(';').append(r.absent).append(';').append(joined(r.nums)).append('\n');}
    StringBuilder csv=new StringBuilder("Tipo;Grupo;Dezena;Frequencia ultimos 100;Atraso individual;Ausente ultimo\n");for(int n:b.nums)csv.append("Tirinha;20 fixas;").append(fmt(n)).append(';').append(p.recent[n]).append(';').append(p.delay[n]).append(';').append(p.absent.contains(n)?"SIM":"NAO").append('\n');for(Map.Entry<String,ArrayList<Integer>> e:p.byGroup.entrySet())for(int n:e.getValue())csv.append("Consultivo;").append(e.getKey()).append(';').append(fmt(n)).append(';').append(p.recent[n]).append(';').append(p.delay[n]).append(';').append(p.absent.contains(n)?"SIM":"NAO").append('\n');
    Uri pdf=savePdf("LOTOMANIA_50_FALHAS_ESPELHO_V11.pdf",report.toString(),rank.toString(),chosen,mirror);saveText("LOTOMANIA_50_FALHAS_ESPELHO_V11.csv",csv.toString());saveText("LOTOMANIA_RANKING_ATRASO_V11.csv",rank.toString());
    status("RELATORIOS GERADOS!\n\nPasta: Download/Lotomania combinações de tiras\n\nPDF: LOTOMANIA_50_FALHAS_ESPELHO_V11.pdf\n\nCSV: 50 falhas e ranking das 137 combinacoes\n\n"+report);
    runOnUiThread(()->{Button open=button("ABRIR PDF");root.addView(open,3);open.setOnClickListener(v->{try{Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(pdf,"application/pdf");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Exception ex){new AlertDialog.Builder(this).setMessage("PDF salvo em Downloads, mas nao existe aplicativo disponivel para abri-lo.").setPositiveButton("OK",null).show();}});});
  }
  Uri insert(String name,String mime)throws Exception{ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,name);v.put(MediaStore.Downloads.MIME_TYPE,mime);v.put(MediaStore.Downloads.RELATIVE_PATH,"Download/Lotomania combinações de tiras/");Uri u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("Falha ao criar "+name);return u;}
  void saveText(String name,String text)throws Exception{Uri uri=insert(name,"text/csv");try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException("Sem acesso a gravacao");out.write(('\ufeff'+text).getBytes(StandardCharsets.UTF_8));}}
  // Volante de estudo: 10 colunas x 10 linhas, 01..99 e 00.
  // Pagina 1 marca as 50 dezenas escolhidas para falha.
  // Pagina 2 marca as 50 dezenas complementares do espelho.
  void drawVolante(PdfDocument pdf, TreeSet<Integer> marked, String subtitle, int pageNo){
    PdfDocument.Page page=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,pageNo).create());
    Canvas c=page.getCanvas();c.drawColor(Color.WHITE);
    Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(Color.rgb(239,149,52));c.drawRoundRect(35,32,560,99,10,10,p);
    p.setColor(Color.WHITE);p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));p.setTextSize(29);
    c.drawText("lotomania",209,75,p);
    p.setColor(Color.rgb(95,75,38));p.setTextSize(13);
    c.drawText("LOTO MANIA CAMPEAO V11 - "+subtitle,35,126,p);
    c.drawText("50 dezenas marcadas | Volante para estudo",35,147,p);
    float left=43,top=178,w=50.5f,h=49.5f;
    for(int row=0;row<10;row++)for(int col=0;col<10;col++){
      int n=row*10+col+1; if(n==100)n=100;
      float x=left+col*w,y=top+row*h;
      p.setColor(Color.rgb(250,245,226));p.setStyle(Paint.Style.FILL);c.drawRect(x,y,x+w,y+h,p);
      if(marked.contains(n)){
        p.setColor(Color.rgb(255,211,40));c.drawCircle(x+w/2,y+h/2,18,p);
        p.setColor(Color.rgb(96,68,0));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);c.drawCircle(x+w/2,y+h/2,18,p);p.setStyle(Paint.Style.FILL);
      }
      p.setColor(marked.contains(n)?Color.BLACK:Color.rgb(130,119,102));p.setTextSize(15);p.setTypeface(Typeface.create(Typeface.DEFAULT,marked.contains(n)?Typeface.BOLD:Typeface.NORMAL));
      String label=fmt(n);c.drawText(label,x+16,y+30,p);
    }
    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f);p.setColor(Color.rgb(62,135,175));
    for(int col=2;col<=8;col+=2){float x=left+col*w;c.drawLine(x,top,x,top+10*h,p);}
    c.drawLine(left,top+5*h,left+10*w,top+5*h,p);p.setStyle(Paint.Style.FILL);
    p.setColor(Color.rgb(105,79,46));p.setTextSize(12);
    c.drawText("01-50: parte superior  |  51-00: parte inferior",43,top+10*h+28,p);
    c.drawText("Modelo para estudo - nao e comprovante de aposta",43,top+10*h+49,p);
    pdf.finishPage(page);
  }
  Uri savePdf(String name,String report,String rank,TreeSet<Integer> chosen,TreeSet<Integer> mirror)throws Exception{
    Uri uri=insert(name,"application/pdf");PdfDocument pdf=new PdfDocument();
    try{
      drawVolante(pdf,chosen,"50 DEZENAS PARA FALHA",1);
      drawVolante(pdf,mirror,"50 DEZENAS DO ESPELHO",2);
      Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.NORMAL));paint.setTextSize(9);
      int page=2,y=0;PdfDocument.Page pg=null;Canvas canvas=null;
      ArrayList<String> lines=new ArrayList<>();
      for(String raw:(report+"\nRANKING COMPLETO DAS 137 COMBINACOES\n"+rank.replace(';',' ')).split("\n")){
        if(raw.length()==0){lines.add("");continue;}
        for(int i=0;i<raw.length();i+=96)lines.add(raw.substring(i,Math.min(i+96,raw.length())));
      }
      for(String line:lines){if(pg==null||y>785){if(pg!=null)pdf.finishPage(pg);pg=pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,++page).create());canvas=pg.getCanvas();canvas.drawColor(Color.WHITE);y=35;}
        paint.setColor(Color.BLACK);canvas.drawText(line,20,y,paint);y+=13;
      }
      if(pg!=null)pdf.finishPage(pg);
      try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new IOException("Sem acesso a gravacao PDF");pdf.writeTo(out);}
    }finally{pdf.close();}
    return uri;
  }
}
