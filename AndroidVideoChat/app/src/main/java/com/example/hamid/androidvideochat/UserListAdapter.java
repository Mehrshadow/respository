package com.quickblox.AndroidVideoChat;package com.example.hamid.androidvideochat;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.quickblox.module.users.model.QBUser;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Andrew Dmitrenko
 * Date: 6/17/13
 * Time: 10:24 AM
 */
public class UserListAdapter extends BaseAdapter {

    private List<QBUser> userList;
    private LayoutInflater layoutInflater;

    public UserListAdapter(Context context, List<QBUser> userList) {
        this.userList = userList;
        removeExtraUser();
        layoutInflater = LayoutInflater.from(context);

    }

    private void removeExtraUser() {
        for (QBUser user : userList) {
            if (DataHolder.getInstance().getCurrentQbUser().getLogin().equals(user.getLogin())) {
                Log.d("removeExtraUser", user.getLogin() + "--->" + String.valueOf(user.getId()));
                userList.remove(user);
                break;
            }
        }
    }

    @Override
    public int getCount() {
        return userList.size();
    }

    @Override
    public Object getItem(int i) {
        return userList.get(i);
    }

    @Override
    public long getItemId(int i) {
        return 0;
    }

    @Override
    public View getView(int i, View convertView, ViewGroup parent) {
        ViewHolder viewHolder = null;
        if (convertView == null) {
            convertView = layoutInflater.inflate(R.layout.item_user_list, parent, false);
            viewHolder = new ViewHolder();
            viewHolder.userName = (TextView) convertView.findViewById(R.id.userNameTextView);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }
        viewHolder.userName.setText(userList.get(i).getLogin());

        return convertView;
    }


    public static class ViewHolder {
        TextView userName;
    }
}
