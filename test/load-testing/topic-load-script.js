import http from "k6/http";
import { sleep } from "k6";

export const options = {
    vus: 100,
    duration: "1m",
};

export default function () {
    const url = `${__ENV.APIM_URL}/topics/messages`;

    const message = JSON.stringify({
        message: "Hello World - Timestamp: " + new Date().getTime(),
        sessionId: "123456",
    });

    const params = {
        headers: {
            "Content-Type": "application/json",
        },
    };

    http.post(url, message, params);
    sleep(1);
}