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
package org.teavm.javac;

import java.util.Iterator;
import org.teavm.callgraph.CallGraph;
import org.teavm.callgraph.CallGraphNode;
import org.teavm.callgraph.CallSite;
import org.teavm.diagnostics.DefaultProblemTextConsumer;
import org.teavm.diagnostics.Problem;
import org.teavm.javac.protocol.TeaVMDiagnosticMessage;
import org.teavm.javac.protocol.WorkerMessage;
import org.teavm.jso.browser.Window;
import org.teavm.model.CallLocation;
import org.teavm.model.MethodReference;
import org.teavm.model.TextLocation;
import org.teavm.vm.TeaVM;

public final class TeaVMProblemRenderer {
    private TeaVMProblemRenderer() {
    }

    public static void describeProblems(WorkerMessage request, String fileName, TeaVM vm) {
        CallGraph cg = vm.getDependencyInfo().getCallGraph();
        DefaultProblemTextConsumer consumer = new DefaultProblemTextConsumer();
        for (Problem problem : vm.getProblemProvider().getProblems()) {
            consumer.clear();
            problem.render(consumer);
            StringBuilder sb = new StringBuilder();
            sb.append(consumer.getText());

            TeaVMDiagnosticMessage message = Client.createMessage();
            message.setId(request.getId());
            message.setLineNumber(-1);
            message.setCommand("diagnostic");
            message.setSeverity(problem.getSeverity().name());
            message.setFileName(null);

            renderCallStack(fileName, message, cg, problem.getLocation(), sb);

            String problemText = sb.toString();
            switch (problem.getSeverity()) {
                case ERROR:
                    System.out.println("ERROR: " + problemText);
                    break;
                case WARNING:
                    System.out.println("WARNING: " + problemText);
                    break;
            }

            message.setText(sb.toString());
            Window.worker().postMessage(message);
        }
    }

    public static void renderCallStack(String fileName, TeaVMDiagnosticMessage targetMessage,
            CallGraph cg, CallLocation location, StringBuilder sb) {
        if (location == null) {
            return;
        }
        sb.append("\n    at ");
        renderCallLocation(location.getMethod(), location.getSourceLocation(), sb);
        if (location.getMethod() != null) {
            CallGraphNode node = cg.getNode(location.getMethod());
            for (int i = 0; i < 100; ++i) {
                Iterator<? extends CallSite> callSites = node.getCallerCallSites().iterator();
                if (!callSites.hasNext()) {
                    break;
                }
                CallSite callSite = callSites.next();
                if (callSite.getLocation() != null && callSite.getLocation().getFileName().equals(fileName)) {
                    targetMessage.setFileName(callSite.getLocation().getFileName());
                    targetMessage.setLineNumber(callSite.getLocation().getLine());
                    return;
                }
                sb.append("\n    at ");
                renderCallLocation(callSite.getCaller().getMethod(), callSite.getLocation(), sb);
                node = callSite.getCaller();
            }
        }
    }

    public static void renderCallLocation(MethodReference method, TextLocation location, StringBuilder sb) {
        if (method != null) {
            sb.append(method.getClassName() + "." + method.getName());
        } else {
            sb.append("unknown method");
        }
        if (location != null) {
            sb.append("(");
            String fileName = location.getFileName();
            if (fileName != null) {
                sb.append(fileName.substring(fileName.lastIndexOf('/') + 1));
                sb.append(':');
            }
            sb.append(location.getLine());
            sb.append(')');
        }
    }
}
