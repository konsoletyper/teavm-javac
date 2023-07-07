/*
 *  Copyright 2017 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.teavm.javac.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PositionIndexer {
    private int[] lines;

    public PositionIndexer(String text) {
        List<Integer> linesBuilder = new ArrayList<>();
        linesBuilder.add(0);
        for (int i = 0; i < text.length(); ++i) {
            if (text.charAt(i) == '\n') {
                linesBuilder.add(i + 1);
            }
        }

        lines = new int[linesBuilder.size()];
        for (int i = 0; i < linesBuilder.size(); ++i) {
            lines[i] = linesBuilder.get(i);
        }
    }

    public Position getPositionAt(int offset, boolean start) {
        int line = Arrays.binarySearch(lines, offset);
        if (line < 0) {
            line = -line - 2;
        }
        int column = offset - lines[line];
        if (column > 0 && line + 1 < lines.length && offset + 2 >= lines[line + 1]) {
            if (start) {
                --column;
            } else {
                column = 0;
                ++line;
            }
        }
        return new Position(line, column);
    }
}
