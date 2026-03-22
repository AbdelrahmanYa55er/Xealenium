package com.visual;
public final class TextSimilarity {
    private TextSimilarity(){}
    public static double score(String a,String b){
        if(a==null)a="";if(b==null)b="";
        a=a.trim().toLowerCase();b=b.trim().toLowerCase();
        if(a.isEmpty()&&b.isEmpty())return 1.0;
        if(a.isEmpty()||b.isEmpty())return 0.0;
        if(a.equals(b))return 1.0;
        return 1.0-(double)lev(a,b)/Math.max(a.length(),b.length());
    }
    private static int lev(String a,String b){
        int m=a.length(),n=b.length();
        int[] p=new int[n+1],c=new int[n+1];
        for(int j=0;j<=n;j++)p[j]=j;
        for(int i=1;i<=m;i++){
            c[0]=i;
            for(int j=1;j<=n;j++){
                int cost=a.charAt(i-1)==b.charAt(j-1)?0:1;
                c[j]=Math.min(Math.min(c[j-1]+1,p[j]+1),p[j-1]+cost);
            }
            int[] t=p;p=c;c=t;
        }
        return p[n];
    }
}
