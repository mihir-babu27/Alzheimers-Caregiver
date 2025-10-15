package com.mihir.alzheimerscaregiver.caretaker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying location time points in a RecyclerView
 */
public class LocationTimePointsAdapter extends RecyclerView.Adapter<LocationTimePointsAdapter.TimePointViewHolder> {

    private List<HistoryActivity.LocationPoint> locationPoints;
    private SimpleDateFormat timeFormat;
    
    public LocationTimePointsAdapter() {
        this.locationPoints = new ArrayList<>();
        this.timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    }
    
    public void updateLocationPoints(List<HistoryActivity.LocationPoint> newPoints) {
        this.locationPoints.clear();
        this.locationPoints.addAll(newPoints);
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public TimePointViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_time_point, parent, false);
        return new TimePointViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull TimePointViewHolder holder, int position) {
        HistoryActivity.LocationPoint point = locationPoints.get(position);
        
        holder.textTime.setText(timeFormat.format(new Date(point.timestamp)));
        holder.textLatLng.setText(String.format(Locale.getDefault(), 
                "%.6f, %.6f", point.latitude, point.longitude));
        
        // Show position number
        holder.textPosition.setText(String.valueOf(position + 1));
    }
    
    @Override
    public int getItemCount() {
        return locationPoints.size();
    }
    
    static class TimePointViewHolder extends RecyclerView.ViewHolder {
        TextView textTime;
        TextView textLatLng;
        TextView textPosition;
        
        public TimePointViewHolder(@NonNull View itemView) {
            super(itemView);
            textTime = itemView.findViewById(R.id.textTime);
            textLatLng = itemView.findViewById(R.id.textLatLng);
            textPosition = itemView.findViewById(R.id.textPosition);
        }
    }
}