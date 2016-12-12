package classes;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.example.fcc.udptest.G;
import com.example.fcc.udptest.MainActivity;
import com.example.fcc.udptest.MakeCallActivity;
import com.example.fcc.udptest.MakeVideoCallActivity;
import com.example.fcc.udptest.R;

import java.net.InetAddress;
import java.util.List;
import java.util.logging.*;

/**
 * Created by HaMiD on 12/10/2016.
 */

public class RcyContactsAdapter extends RecyclerView.Adapter<RcyContactsHolder> {

    public final static String EXTRA_C_Name = "hw.dt83.udpchat.CONTACT";
    public final static String EXTRA_C_Ip = "hw.dt83.udpchat.IP";
    public final static String EXTRA_DISPLAYNAME = "hw.dt83.udpchat.DISPLAYNAME";

    List<Contacts> contactsList;
    MainActivity mainActivity = new MainActivity();
    Context context;

    public RcyContactsAdapter(List<Contacts> contactList, Context context) {

        this.contactsList = contactList;
        this.context = context;

    }


    @Override
    public RcyContactsHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.rcy_contacts, parent, false);
        return new RcyContactsHolder(v);
    }

    @Override
    public void onBindViewHolder(RcyContactsHolder holder, final int position) {

        holder.Txt_C_Name.setText(contactsList.get(position).getC_Name());


        holder.Btn_VoiceCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MakeVideoCall(contactsList.get(position).getC_Name(),contactsList.get(position).getC_Ip());
                Logger.i("RcyContactsAdapter", "onBindViewHolder", "Send MakeVoiceCall Req to >> " + contactsList.get(position).getC_Ip());
            }
        });

        holder.Btn_VideoCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                MakeVoiceCall(contactsList.get(position).getC_Name(),contactsList.get(position).getC_Ip());
                Logger.i("RcyContactsAdapter", "onBindViewHolder", "Send MakeVideoCall Req to >> " + contactsList.get(position).getC_Ip());

            }
        });

    }

    @Override
    public int getItemCount() {
        return contactsList.size();
    }

    public void MakeVoiceCall(String C_Name, InetAddress C_Ip) {

        G.IN_CALL = true;

        // Send this information to the MakeCallActivity and start that activity
        Intent intent = new Intent(context, MakeCallActivity.class);
        intent.putExtra(EXTRA_C_Name, C_Name);
        intent.putExtra(EXTRA_C_Ip, C_Ip);
        intent.putExtra(EXTRA_DISPLAYNAME, "SERVER");
        context.startActivity(intent);
    }

    public void MakeVideoCall(String C_Name, InetAddress C_Ip) {

        G.IN_CALL= true;

        Intent i = new Intent(context, MakeVideoCallActivity.class);
        i.putExtra(EXTRA_C_Name, C_Name);
        i.putExtra(EXTRA_C_Ip, C_Ip);
        i.putExtra(EXTRA_DISPLAYNAME, "SERVER");
        context.startActivity(i);
    }

}
