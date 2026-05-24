package com.saasaa.lawncar.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.saasaa.lawncar.R;
import com.saasaa.lawncar.model.AppInfo;

import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    private final List<AppInfo> apps;
    private final OnAppActionListener listener;

    public interface OnAppActionListener {
        void onAppClick(AppInfo app);
        void onAppLongClick(AppInfo app, View view);
    }

    public AppAdapter(List<AppInfo> apps, OnAppActionListener listener) {
        this.apps = apps;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo app = apps.get(position);

        holder.label.setText(app.label);
        holder.label.setTextColor(
                com.google.android.material.color.MaterialColors.getColor(
                        holder.itemView,
                        com.google.android.material.R.attr.colorOnSurface
                )
        );


        if (app.folderColor != 0) {
            holder.star.setTextColor(app.folderColor);
            holder.star.setVisibility(View.VISIBLE);
        } else {
            holder.star.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onAppClick(app);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onAppLongClick(app, v);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView label;
        TextView star;

        ViewHolder(View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.appLabel);
            star = itemView.findViewById(R.id.appStar);
        }
    }
}
