import {Config} from '@remotion/cli/config';

Config.setVideoImageFormat('jpeg');
Config.setJpegQuality(95);
Config.setCodec('h264');
Config.setPixelFormat('yuv420p');
Config.setCrf(15);
Config.setAudioCodec('aac');
Config.setDelayRenderTimeoutInMilliseconds(180000);
Config.setOverwriteOutput(true);
