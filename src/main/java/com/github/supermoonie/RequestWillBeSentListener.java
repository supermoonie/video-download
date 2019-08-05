package com.github.supermoonie;

import com.github.supermoonie.event.Event;
import com.github.supermoonie.event.network.LoadingFailed;
import com.github.supermoonie.event.network.LoadingFinished;
import com.github.supermoonie.event.network.RequestWillBeSent;
import com.github.supermoonie.exception.NetworkLoadingFailedException;
import com.github.supermoonie.listener.AbstractEventListener;
import com.github.supermoonie.util.StringUtils;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author supermoonie
 * @date 2019/8/5
 */
public class RequestWillBeSentListener extends AbstractEventListener {

    private final String matchUrl;
    private final AtomicReference<String> requestIdRef = new AtomicReference();
    private RequestWillBeSent requestWillBeSent;

    public RequestWillBeSentListener(String matchUrl) {
        if (StringUtils.isEmpty(matchUrl)) {
            throw new IllegalArgumentException("matchUrl is empty!");
        } else {
            this.matchUrl = matchUrl.replaceAll("\\*", ".*");
        }
    }

    @Override
    public void onEvent(Event event, Object obj) {
        switch (event) {
            case NetworkRequestWillBeSent:
                RequestWillBeSent request = (RequestWillBeSent)obj;
                if (request.getRequest().getUrl().substring(5).contains("http")) {
                    break;
                }
                if (request.getRequest().getUrl().matches(matchUrl)) {
                    requestIdRef.set(request.getRequestId());
                    requestWillBeSent = request;
                }
                break;
            case NetworkLoadingFailed:
                LoadingFailed loadingFailed = (LoadingFailed) obj;
                if (loadingFailed.getRequestId().equals(requestIdRef.get())) {
                    throw new NetworkLoadingFailedException(loadingFailed.getErrorText());
                }
                break;
            case NetworkLoadingFinished:
                LoadingFinished loadingFinished = (LoadingFinished) obj;
                if (loadingFinished.getRequestId().equals(requestIdRef.get())) {
                    latch.countDown();
                }
                break;
            default:
                break;
        }

    }

    public RequestWillBeSent getRequestWillBeSent() {
        return requestWillBeSent;
    }
}
