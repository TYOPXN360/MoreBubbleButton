#!/bin/bash
sed -i '/if ("onNotificationUpdated".equals(name) || "updateBubbleButton".equals(name) || "setExpandedChild".equals(name)) {/c \
                        if ("onNotificationUpdated".equals(name) || "updateBubbleButton".equals(name) || "setExpandedChild".equals(name)) {\n                            Object row = getFieldSystemUi(contentView, "mContainingNotification");\n                            prepareBubbleMetadata(getNotificationEntryFromRow(row));\n                        }' app/src/main/java/com/floatwindow/morebubblebutton/MoreBubbleHookModule.java
