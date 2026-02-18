package com.scholar.view;

import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

public class DraggableListView<T> extends ListView<T> {

    public DraggableListView() {
        this.setCellFactory(param -> new DraggableCell());
    }

    private class DraggableCell extends ListCell<T> {
        @Override
        protected void updateItem(T item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.toString()); // Make sure your objects have a good toString()!
            }
        }

        public DraggableCell() {
            // 1. DRAG DETECTED
            setOnDragDetected(event -> {
                if (getItem() == null) return;
                Dragboard db = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(String.valueOf(getIndex())); // Store Index
                db.setContent(content);
                event.consume();
            });

            // 2. DRAG OVER (Show visual cue)
            setOnDragOver(event -> {
                if (event.getGestureSource() != this && event.getDragboard().hasString()) {
                    event.acceptTransferModes(TransferMode.MOVE);
                }
                event.consume();
            });

            // 3. DROP (Swap items)
            setOnDragDropped(event -> {
                if (getItem() == null) return;
                
                Dragboard db = event.getDragboard();
                boolean success = false;
                
                if (db.hasString()) {
                    int draggedIdx = Integer.parseInt(db.getString());
                    int thisIdx = getIndex();
                    
                    ObservableList<T> items = getListView().getItems();
                    T draggedItem = items.get(draggedIdx);
                    
                    // SWAP IN UI
                    items.remove(draggedIdx);
                    items.add(thisIdx, draggedItem);
                    
                    // TODO: Call Database Update here!
                    // courseService.updateTopicOrder(draggedItem.getId(), thisIdx);
                    
                    success = true;
                }
                event.setDropCompleted(success);
                event.consume();
            });
        }
    }
}