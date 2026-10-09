#!/bin/bash
sed -i '/if (!enabled || !validNotification || !isHeadsUpPopupAvailable(row)) {/i \
            if (enabled && validNotification && isHeadsUpPopupAvailable(row)) {\n                hideBubbleButtonInHeadsUp(row);\n            }' app/src/main/java/com/floatwindow/morebubblebutton/MoreBubbleHookModule.java
