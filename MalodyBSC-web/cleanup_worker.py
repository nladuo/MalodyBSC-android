import os
import time


while True:
    for i in range(60):
        print("休眠中。。。。。")
        time.sleep(60)
    print("开始清理.....")
    unix = time.time()
    INT_DEL_HOUR = 6
    for p in os.listdir("upload_dir/tmp/"):
        fpath = f"upload_dir/tmp/{p}"
        interval = (unix - os.path.getctime(fpath))/60/60
        if os.path.isdir(fpath):
            print(fpath)
            if interval > INT_DEL_HOUR:
                os.system(f"rm -r {fpath}")

        if fpath.endswith("mcz"):
            if interval > INT_DEL_HOUR:
                os.remove(fpath)

        if fpath.endswith("osz"):
            if interval > INT_DEL_HOUR:
                os.remove(fpath)

