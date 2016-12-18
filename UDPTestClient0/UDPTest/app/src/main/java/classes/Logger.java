package classes;

import android.util.Log;

/**
 * Created by HaMiD on 12/10/2016.
 */

public class Logger {

    public static void i(String ClassName,String Operation,String Resault){
        Log.i("LOG CLIENT : "+ClassName," | "+Operation+" | "+Resault);
    }

    public static void d(String ClassName,String Operation,String Resault){
        Log.d("LOG CLIENT : "+ClassName," | "+Operation+" | "+Resault);
    }

    public static void e(String ClassName,String Operation,String Resault){
        Log.e("LOG CLIENT : "+ClassName," | "+Operation+" | "+Resault);
    }
}
