package classes;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import ir.jahanmir.videocall.R;

/**
 * Created by HaMiD on 12/10/2016.
 */

public class RcyContactsHolder extends RecyclerView.ViewHolder {

    TextView Txt_C_Name;
    Button Btn_VoiceCall;
    Button Btn_VideoCall;


    public RcyContactsHolder(View itemView) {
        super(itemView);
        Txt_C_Name = (TextView)itemView.findViewById(R.id.txt_c_name);
        Btn_VideoCall = (Button)itemView.findViewById(R.id.btn_videocall);
        Btn_VoiceCall = (Button)itemView.findViewById(R.id.btn_voicecall);

    }
}
