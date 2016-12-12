package classes;

import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.example.fcc.udptest.MainActivity;
import com.example.fcc.udptest.MakeVideoCallActivity;

import java.util.List;
import java.util.logging.*;

/**
 * Created by HaMiD on 12/10/2016.
 */

public class RcyContactsAdapter extends RecyclerView.Adapter<RcyContactsHolder> {

    List<Contacts> contactsList;
    MainActivity mainActivity = new MainActivity();

    public RcyContactsAdapter(List<Contacts> contactList) {

        this.contactsList = contactList;

    }


    @Override
    public RcyContactsHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return null;
    }

    @Override
    public void onBindViewHolder(RcyContactsHolder holder, final int position) {

        holder.Txt_C_Name.setText(contactsList.get(position).getC_Name());
        holder.Btn_VoiceCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mainActivity.MakeVoiceCall(
                        contactsList.get(position).getC_Name()
                        , contactsList.get(position).getC_Ip()
                );
                Logger.i("RcyContactsAdapter", "onBindViewHolder", "Send MakeVoiceCall Req to >> "+contactsList.get(position).getC_Ip());
            }
        });

        holder.Btn_VideoCall.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mainActivity.MakeVideoCall(contactsList.get(position).getC_Name()
                        , contactsList.get(position).getC_Ip());
                Logger.i("RcyContactsAdapter", "onBindViewHolder", "Send MakeVideoCall Req to >> "+contactsList.get(position).getC_Ip());

            }
        });

    }

    @Override
    public int getItemCount() {
        return contactsList.size();
    }

}
