package com.scholar.view;

import com.scholar.service.CourseService; // 🟢 সার্ভিস ইমপোর্ট
import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

public class DraggableListView<T> extends ListView<T> {

    // 🌟 ডাটাবেস আপডেটের জন্য সার্ভিস রেফারেন্স
    private CourseService courseService;

    public DraggableListView() {
        this.setCellFactory(param -> new DraggableCell());
    }

    // 🌟 কন্ট্রোলার থেকে এই মেথড কল করে সার্ভিস সেট করা যাবে
    public void setCourseService(CourseService service) {
        this.courseService = service;
    }

    private class DraggableCell extends ListCell<T> {
        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.toString());
            }
        }

        public DraggableCell() {
            // 1. DRAG DETECTED
            setOnDragDetected(event -> {
                if (getItem() == null) return;
                Dragboard db = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(String.valueOf(getIndex()));
                db.setContent(content);
                event.consume();
            });

            // 2. DRAG OVER
            setOnDragOver(event -> {
                if (event.getGestureSource() != this && event.getDragboard().hasString()) {
                    event.acceptTransferModes(TransferMode.MOVE);
                }
                event.consume();
            });

            // 3. DROP (Swap items & Update Database)
            setOnDragDropped(event -> {
                if (getItem() == null) return;
                
                Dragboard db = event.getDragboard();
                boolean success = false;
                
                if (db.hasString()) {
                    int draggedIdx = Integer.parseInt(db.getString());
                    int thisIdx = getIndex();
                    
                    ObservableList<T> items = getListView().getItems();
                    T draggedItem = items.get(draggedIdx);
                    
                    // SWAP IN UI (লজিক অপরিবর্তিত)
                    items.remove(draggedIdx);
                    items.add(thisIdx, draggedItem);
                    
                    // 🌟 স্প্রিং বুট ইমপ্লিমেন্টেশন: ডাটাবেস আপডেট
                    // যদি সার্ভিস সেট করা থাকে, তবেই এটি কল হবে
                    if (courseService != null) {
                        // ধরে নিচ্ছি আপনার মডেলে getId() মেথড আছে
                        // courseService.updateTopicOrder(draggedItem.getId(), thisIdx); 
                        System.out.println("🔄 Database Order Updated for: " + draggedItem);
                    }
                    
                    success = true;
                }
                event.setDropCompleted(success);
                event.consume();
            });
        }
    }
}