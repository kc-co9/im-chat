/**
 * get请求
 * @param url
 * @param headers
 * @param successFunction
 */
function get(url, headers, successFunction) {
    $.ajax({
        type: "get",
        url: url,
        dataType: "text",
        contentType: "application/json",      //网上很多介绍加上此参数的，后来我发现不加入这个参数才会请求成功。
        headers: headers,
        data: null,
        success: successFunction
    });
}


/**
 * post请求
 * @param url
 * @param headers
 * @param data
 * @param successFunction
 * @param errorFunction
 */
function post(url, headers, data, successFunction, errorFunction) {
    $.ajax({
        type: "post",
        url: url,
        dataType: "text",
        contentType: "application/json",      //网上很多介绍加上此参数的，后来我发现不加入这个参数才会请求成功。
        headers: headers,
        data: JSON.stringify(data),
        success: successFunction,
        error: errorFunction
    });
}

/**
 * post请求
 * @param url
 * @param data
 * @param successFunction
 */
// function post(url, data, successFunction) {
//     post(url , data , successFunction , function () {
//         console.log("异常");
//     })
// }

/**
 * 存token
 */
function saveToken(token) {
    sessionStorage.setItem("token", token);
}

/**
 * 取token
 */
function getToken() {
    return sessionStorage.getItem("token");
}
