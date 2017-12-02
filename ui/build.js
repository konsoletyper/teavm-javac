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

var fs = require("fs");

if (!fs.existsSync("target/static")) {
    fs.mkdirSync("target/static");
}
if (!fs.existsSync("target/static/fonts")) {
    fs.mkdirSync("target/static/fonts");
}
if (!fs.existsSync("target/static/css")) {
    fs.mkdirSync("target/static/css");
}

copy("node_modules/bootstrap/dist/css/bootstrap.min.css", "target/static/css/bootstrap.min.css");

var files = fs.readdirSync("node_modules/bootstrap/dist/fonts/");
for (var i = 0; i < files.length; ++i) {
    var fileName = files[i];
    copy("node_modules/bootstrap/dist/fonts/" + fileName, "target/static/fonts/" + fileName);
}

function copy(from, to) {
    fs.createReadStream(from).pipe(fs.createWriteStream(to));
}