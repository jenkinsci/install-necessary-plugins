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

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientFactory;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.glassfish.jersey.jettison.JettisonFeature;

/**
 * Installs Jenkins plugins necessary for a putative job to be created.
 */
public class Installer {

    private final Client client;
    private final URL jenkins;

    /**
     * Preconfigures an installer.
     * @param client a Jersey client (see {@link ClientFactory})
     * @param jenkins a Jenkins instance (root URL)
     */
    public Installer(Client client, URL jenkins) {
        if (!jenkins.toString().endsWith("/")) {
            throw new IllegalArgumentException("Jenkins URL must end with a slash (/)");
        }
        this.client = client;
        client.configuration().register(JettisonFeature.class);
        this.jenkins = jenkins;
    }

    /**
     * Asks that plugin be installed and waits for that to happen.
     * @param configXml config file contents e.g. for a job
     * @throws IOException in case of problems
     * @throws InterruptedException if interrupted while polling
     */
    @SuppressWarnings("SleepWhileInLoop")
    @edu.umd.cs.findbugs.annotations.SuppressWarnings("SF_SWITCH_NO_DEFAULT")
    public void run(byte[] configXml) throws IOException, InterruptedException, JSONException {
        Response r = client.target(jenkins + "pluginManager/installNecessaryPlugins").request().post(Entity.xml(configXml));
        try { // XXX is there a cleaner way to just assert HTTP_OK?
            if (r.getStatus() != 200) {
                throw new IOException("POSTing to pluginManager/installNecessaryPlugins failed");
            }
        } finally {
            r.close();
        }
        installNecessaryPluginsSucceeded();
        boolean requireRestart = false;
        Map<Integer,Status> statuses = new HashMap<Integer,Status>();
        while (true) {
            JSONArray jobs = client.target(jenkins + "updateCenter/api/json?tree=jobs[id,type,status[type],plugin[name,version]]").request().get(JSONObject.class).getJSONArray("jobs");
            boolean done = true;
            for (int i = 0; i < jobs.length(); i++) {
                JSONObject job = jobs.getJSONObject(i);
                if (!job.getString("type").equals("InstallationJob")) {
                    continue;
                }
                int id = job.getInt("id");
                Status status = Status.valueOf(job.getJSONObject("status").getString("type"));
                JSONObject plugin = job.getJSONObject("plugin");
                if (status != statuses.put(id, status)) {
                    job(plugin.getString("name"), plugin.getString("version"), status);
                }
                switch (status) {
                case SuccessButRequiresRestart:
                    requireRestart = true;
                    break;
                case Failure:
                    throw new IOException("failed to install " + plugin.getString("name"));
                case Pending:
                case Installing:
                    done = false;
                }
            }
            if (done) {
                break;
            }
            waitingForJobs();
            Thread.sleep(1000);
        }
        if (requireRestart) {
            restarting();
            String orig = session();
            // Do not bother with status 503.
            client.target(jenkins + "updateCenter/safeRestart").request().post(Entity.text("")).close();
            while (true) {
                waitingForRestart();
                Thread.sleep(5000);
                String nue = session();
                if (nue != null && !orig.equals(nue)) {
                    break;
                }
            }
        }
    }

    private String session() {
        return client.target(jenkins + "updateCenter/api/json?tree=nothing").request().get().getHeaderString("X-Jenkins-Session");
    }

    /**
     * Callback method when the initial request to install plugins has succeeded.
     */
    protected void installNecessaryPluginsSucceeded() {}

    /**
     * Callback method when an update center job is detected or updated.
     * @param plugin name of plugin, e.g. {@code mercurial}
     * @param version e.g. {@code 1.42}
     * @param status what is going on
     */
    protected void job(String plugin, String version, Status status) {}

    /**
     * Callback method when waiting for jobs to finish.
     */
    protected void waitingForJobs() {}

    /**
     * Callback method when about to restart Jenkins to finish installation.
     */
    protected void restarting() {}

    /**
     * Callback method when waiting for Jenkins to restart.
     */
    protected void waitingForRestart() {}

    public enum Status {Failure, Installing, Pending, Success, SuccessButRequiresRestart}

}
