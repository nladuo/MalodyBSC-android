from flask import Flask, send_from_directory, request
from backend.beatmap_helper import *
from backend.utils import *
import traceback
from redis import StrictRedis
import redis_lock


conn = StrictRedis()
app = Flask(__name__, static_folder='dist')

VID = "3324"


@app.route('/bsc')
def index():
    vid = request.args.get("vid")
    if vid == VID:
        return app.send_static_file('index.html')
    else:
        return "激活码已更新"


@app.route('/assets/<path:path>')
def serve_static(path):
    return send_from_directory('./dist/assets', path)


@app.route('/api/get_file/<path:path>')
def serve_get_file(path):
    return send_from_directory('./upload_dir', path)


@app.route('/api/upload_file', methods=["POST"])
def api_upload_file():
    vid = request.args.get("vid")
    if vid != VID:
        return json.dumps({'success': False, 'msg': 'error vid'})
    if request.method == 'POST':
        if 'file' not in request.files:
            return json.dumps({'success': False, 'msg': 'error parameters'})
        file = request.files['file']
        if file.filename == '':
            return json.dumps({'success': False, 'msg': 'no file selected'})
        else:
            if file and allowed_file(file.filename):
                origin_file_name = file.filename
                print(origin_file_name)
                file.save(os.path.join("upload_dir", "tmp", origin_file_name))
                with open(os.path.join("upload_dir", "now_file.txt"), "w") as ft:
                    ft.write(origin_file_name)

                beatmaps = get_beatmaps(create=True)

                if len(beatmaps) != 0:
                    return json.dumps({'success': True, 'beatmaps': beatmaps, 'msg': 'success'})
                else:
                    return json.dumps({'success': False, 'msg': 'cannot find beatmap'})

            else:
                return json.dumps({'success': False, 'msg': 'error file type'})


@app.route('/api/generate_beatmaps')
def api_generate_beatmaps():
    vid = request.args.get("vid")
    if vid != VID:
        return
    try:
        with redis_lock.Lock(conn, name='malody-bsc', expire=200, auto_renewal=True):
            speeds = request.args.get("speeds")
            index = request.args.get("index")
            index = int(index)
            speeds = json.loads(speeds)

            beatmaps = get_beatmaps(create=False)
            beatmap = beatmaps[index]
            _type = beatmap["type"]
            outdir = beatmap["out_dir"]
            json_data = beatmap["json_data"]
            tmp_file = generate_beatmaps(_type, outdir, json_data, speeds)
            return json.dumps({
                "success": True,
                "file": tmp_file
            })
    except:
        traceback.print_exc()
    return json.dumps({
        "success": False
    })


if __name__ == '__main__':
    app.run(port=4776, debug=True, host="0.0.0.0")
