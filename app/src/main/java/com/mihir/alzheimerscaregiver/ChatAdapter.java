package com.mihir.alzheimerscaregiver;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ChatAdapter - RecyclerView adapter for displaying chat messages
 * Handles both user and AI messages with different layouts
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    
    private static final int VIEW_TYPE_USER = 1;
    private static final int VIEW_TYPE_AI = 2;
    
    private List<ChatMessage> messages;
    private SimpleDateFormat timeFormat;
    
    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
        this.timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    }
    
    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isFromUser() ? VIEW_TYPE_USER : VIEW_TYPE_AI;
    }
    
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        
        if (viewType == VIEW_TYPE_USER) {
            View view = inflater.inflate(R.layout.item_chat_user, parent, false);
            return new UserMessageViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_chat_ai, parent, false);
            return new AIMessageViewHolder(view);
        }
    }
    
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        
        if (holder instanceof UserMessageViewHolder) {
            ((UserMessageViewHolder) holder).bind(message);
        } else if (holder instanceof AIMessageViewHolder) {
            ((AIMessageViewHolder) holder).bind(message);
        }
    }
    
    @Override
    public int getItemCount() {
        return messages.size();
    }
    
    /**
     * ViewHolder for user messages
     */
    static class UserMessageViewHolder extends RecyclerView.ViewHolder {
        private TextView messageText;
        private TextView timeText;
        
        public UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.userMessageText);
            timeText = itemView.findViewById(R.id.userTimeText);
        }
        
        public void bind(ChatMessage message) {
            messageText.setText(message.getText());
            timeText.setText(new SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(new Date(message.getTimestamp())));
        }
    }
    
    /**
     * ViewHolder for AI messages
     */
    static class AIMessageViewHolder extends RecyclerView.ViewHolder {
        private TextView messageText;
        private TextView timeText;
        
        public AIMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.aiMessageText);
            timeText = itemView.findViewById(R.id.aiTimeText);
        }
        
        public void bind(ChatMessage message) {
            messageText.setText(message.getText());
            timeText.setText(new SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(new Date(message.getTimestamp())));
        }
    }
}