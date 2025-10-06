package com.mihir.alzheimerscaregiver.reminiscence;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.mihir.alzheimerscaregiver.R;
import com.mihir.alzheimerscaregiver.data.model.StoryEntity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for displaying list of stories
 */
public class StoriesAdapter extends RecyclerView.Adapter<StoriesAdapter.StoryViewHolder> {
    
    public interface OnStoryActionListener {
        void onPlayStory(StoryEntity story);
    }
    
    private List<StoryEntity> stories = new ArrayList<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault());
    private OnStoryActionListener listener;
    
    public StoriesAdapter(OnStoryActionListener listener) {
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public StoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_story, parent, false);
        return new StoryViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull StoryViewHolder holder, int position) {
        StoryEntity story = stories.get(position);
        holder.bind(story);
    }
    
    @Override
    public int getItemCount() {
        return stories.size();
    }
    
    public void updateStories(List<StoryEntity> newStories) {
        this.stories.clear();
        if (newStories != null) {
            this.stories.addAll(newStories);
        }
        notifyDataSetChanged();
    }
    
    class StoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView storyTextView;
        private final TextView timestampTextView;
        private final TextView languageTextView;
        private final MaterialButton playStoryButton;
        
        public StoryViewHolder(@NonNull View itemView) {
            super(itemView);
            storyTextView = itemView.findViewById(R.id.storyTextView);
            timestampTextView = itemView.findViewById(R.id.timestampTextView);
            languageTextView = itemView.findViewById(R.id.languageTextView);
            playStoryButton = itemView.findViewById(R.id.playStoryButton);
        }
        
        public void bind(StoryEntity story) {
            // Set story text
            storyTextView.setText(story.getGeneratedStory());
            
            // Set timestamp
            if (story.getTimestamp() != null) {
                String formattedDate = dateFormat.format(story.getTimestamp());
                timestampTextView.setText(formattedDate);
                timestampTextView.setVisibility(View.VISIBLE);
            } else {
                timestampTextView.setVisibility(View.GONE);
            }
            
            // Set language info
            if (story.getLanguage() != null && !story.getLanguage().equals("English")) {
                languageTextView.setText(story.getLanguage());
                languageTextView.setVisibility(View.VISIBLE);
            } else {
                languageTextView.setVisibility(View.GONE);
            }
            
            // Set up TTS button click
            playStoryButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlayStory(story);
                }
            });
        }
    }
}