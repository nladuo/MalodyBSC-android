<script setup>


import utils from '../utils/utils';
import { ref, reactive } from 'vue'
import { ElLoading } from 'element-plus'
import Api from '../actions/api'
import { ElMessageBox } from 'element-plus'

defineProps({
  msg: String,
})

const vid = utils.getUrlKey("vid")

console.log(vid)

const data = reactive({
  beatmaps: [],
  show_result: false,
  downloadUrl: "",
  selectBeatmap: 0,
  speed: 120,
  result_file: "",
  setBeatmaps(beatmaps) {
    this.beatmaps = beatmaps
  }, 
})

const btnWidth =   (window.innerWidth - 80) + "px"



function handleUploadSuccess(res) {

  if (res.success) {
    console.log(res);
    data.setBeatmaps(res.beatmaps)
    console.log(data.beatmaps);
  } else {
  }
}

function getBeatMaps() {

  const loading = ElLoading.service({
    lock: true,
    text: '生成中请等待...',
    background: 'rgba(0, 0, 0, 0.7)',
  })

  Api.get("/generate_beatmaps", {
      speeds: JSON.stringify([(data.speed * 0.01).toFixed(2)]),
      index: data.selectBeatmap,
      vid: vid
    }, (res)=>{
      loading.close();
      if (res.success) {
        data.show_result = true;
        data.result_file = res.file;
      }else {
        data.show_result = false;
        ElMessageBox.alert(
          '生成谱面失败',
          '失败',
          {
            dangerouslyUseHTMLString: true,
          }
        )
        // this.$message.error("Error occurred，generation for beatmap failed");
      }
    })

}


</script>

<template>
  <el-upload
    class="upload-demo"
    :on-success="handleUploadSuccess"
    :action="'/api/upload_file?vid='+vid"
  >
    <div class="el-upload__text">
      <var-button type="danger" :style="{width: btnWidth}">上传谱面</var-button>
    </div>
  </el-upload>

  <br>

  <div v-if="data.beatmaps.length != 0">
    <label>选择谱面</label>
    <br>
    <var-select v-model="data.selectBeatmap">
      <var-option v-for="item in data.beatmaps" :value="item.id" :label="item.version" />
    </var-select>
    <br>
    <label>速度：{{(data.speed * 0.01).toFixed(2)}}</label>
    <var-slider v-model="data.speed" max="200" min="50" label-visible="false" />
    <var-button type="primary" @click="getBeatMaps" block>生成谱面</var-button>
    <br>
      <a v-if="data.show_result" :href="'/api/get_file/' + data.result_file">
        <var-button type="success" block>下载谱面</var-button>
      </a>

  </div>

 
</template>

<style scoped>
.uuu {
  width: 100%;
}

a{
text-decoration:none;
color: white;
}

</style>
