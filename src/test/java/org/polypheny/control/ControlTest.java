/*
 * Copyright 2019-2021 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.control;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;
import kong.unirest.GetRequest;
import kong.unirest.HttpRequest;
import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.polypheny.control.authentication.AuthenticationDataManager;
import org.polypheny.control.authentication.AuthenticationFileManager;
import org.polypheny.control.client.ClientData;
import org.polypheny.control.client.ClientType;
import org.polypheny.control.client.PolyphenyControlConnector;
import org.polypheny.control.main.ControlCommand;


public class ControlTest {

    private static Thread thread;


    @Before
    public static void start() throws InterruptedException {
        // Precautionary measure: Setting the systemProperty 'testing'
        System.setProperty( "testing", "true" );

        // Backup config file
        File polyphenyDir = new File( System.getProperty( "user.home" ), ".polypheny" );
        if ( polyphenyDir.exists() ) {
            polyphenyDir.renameTo( new File( System.getProperty( "user.home" ), ".polypheny.backup" ) );
        }

        // Create passwd data and an account
        try {
            FileUtils.forceMkdir( new File( System.getProperty( "user.home" ), ".polypheny" ) );
        } catch ( IOException e ) {
//            e.printStackTrace();
        }
        AuthenticationFileManager.getAuthenticationData();
        AuthenticationDataManager.addAuthenticationData( "pc", "pc" );
        AuthenticationFileManager.writeAuthenticationDataToFile();

        thread = new Thread( () -> (new ControlCommand())._run_() );
        thread.start();
        TimeUnit.SECONDS.sleep( 5 );
    }


    @After
    public static void shutdown() throws IOException {
        // Restore config file
        File polyphenyDir = new File( System.getProperty( "user.home" ), ".polypheny" );
        FileUtils.deleteDirectory( polyphenyDir );
        File polyphenyDirBackup = new File( System.getProperty( "user.home" ), ".polypheny.backup" );
        if ( polyphenyDirBackup.exists() ) {
            polyphenyDirBackup.renameTo( polyphenyDir );
        }
    }


    @Test
    public void integrationTestWithAuthEnabled() throws URISyntaxException, InterruptedException {
        System.setProperty( "withAuth", "true" );
        integrationTest();
    }


    @Test
    public void integrationTestWithAuthDisabled() throws URISyntaxException, InterruptedException {
        System.setProperty( "withAuth", "false" );
        integrationTest();
    }


    public void integrationTest() throws URISyntaxException, InterruptedException {
        ClientData clientData = new ClientData( ClientType.BROWSER, "pc", "pc" );
        PolyphenyControlConnector controlConnector = new PolyphenyControlConnector( "localhost:8070", clientData, null );

        // Update and build Polypheny
        controlConnector.updatePolypheny();

        // Start Polypheny
        controlConnector.startPolypheny();
        TimeUnit.SECONDS.sleep( 20 );

        // Execute test query
        GetRequest request = Unirest.get( "{protocol}://{host}:{port}/restapi/v1/res/public.emps" )
                .queryString( "public.emps.empid", "=" + 100 );
        Assert.assertEquals(
                "{\"result\":[{\"public.emps.name\":\"Bill\",\"public.emps.salary\":10000,\"public.emps.empid\":100,\"public.emps.commission\":1000,\"public.emps.deptno\":10}],\"size\":1}",
                executeRest( request ).getBody() );

        // Stop Polypheny
        controlConnector.stopPolypheny();
        TimeUnit.SECONDS.sleep( 5 );
    }


    private HttpResponse<String> executeRest( HttpRequest<?> request ) {
        request.basicAuth( "pa", "" );
        request.routeParam( "protocol", "http" );
        request.routeParam( "host", "127.0.0.1" );
        request.routeParam( "port", "8089" );
        try {
            HttpResponse<String> result = request.asString();
            if ( !result.isSuccess() ) {
                throw new RuntimeException( "Error while executing REST query. Message: " + result.getStatusText() + "  |  URL: " + request.getUrl() );
            }
            return result;
        } catch ( UnirestException e ) {
            throw new RuntimeException( e );
        }
    }

}
