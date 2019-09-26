'''
# convert Keras h5 model to TF Lite model
from tensorflow.contrib import lite
converter = lite.TFLiteConverter.from_keras_model_file( 'model.h5')
tfmodel = converter.convert()
open ("model.tflite" , "wb") .write(tfmodel)
'''

import tensorflow as tf
converter = tf.lite.TFLiteConverter.from_keras_model_file('model.h5')
# converter.optimizations = [tf.lite.Optimize.OPTIMIZE_FOR_SIZE]
tflite_quant_model = converter.convert()
open ("quant_model.tflite" , "wb") .write(tflite_quant_model)

