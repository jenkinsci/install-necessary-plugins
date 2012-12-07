/*
 * The MIT License
 *
 * Copyright 2012 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.installNecessaryPlugins;

import java.io.File;
import java.net.URL;
import javax.ws.rs.client.ClientFactory;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.installNecessaryPlugins.Installer.Status;

/**
 * Command-line version of installer.
 */
public class Main {

    public static void main(String... args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: java -jar this.jar http://jenkins/ config.xml");
        }
        URL jenkins = new URL(args[0]);
        File configXml = new File(args[1]);
        if (!configXml.isFile()) {
            throw new IllegalArgumentException("no such file " + args[1]);
        }
        byte[] payload = FileUtils.readFileToByteArray(configXml);
        System.out.println("Starting installation.");
        new Installer(ClientFactory.newClient(), jenkins) {
            @Override protected void installNecessaryPluginsSucceeded() {
                System.out.println("Successfully sent request.");
            }
            @Override protected void job(String plugin, String version, Status status) {
                System.out.println(plugin + " @" + version + ": " + status);
            }
            @Override protected void waitingForJobs() {
                System.out.println("Waiting for installation...");
            }
            @Override protected void restarting() {
                System.out.println("Restarting...");
            }
            @Override protected void waitingForRestart() {
                System.out.println("Waiting for Jenkins to restart...");
            }
        }.run(payload);
        System.out.println("Done.");
    }

}
