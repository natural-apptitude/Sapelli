/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2014 University College London - ExCiteS group
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package uk.ac.ucl.excites.sapelli.shared.util.android;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;

/**
 * @author mstevens
 *
 */
public final class IntentHelpers
{
	
	private IntentHelpers() {}

	static public void setFlagActivityCleanWhenTaskReset(Intent intent)
	{
		if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP /* = 21 */)
			setFlagActivityCleanWhenTaskResetLollipop(intent);
		else
			setFlagActivityCleanWhenTaskResetPreLollipop(intent);
	}
	
	@SuppressWarnings("deprecation")
	static private void setFlagActivityCleanWhenTaskResetPreLollipop(Intent intent)
	{
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	static private void setFlagActivityCleanWhenTaskResetLollipop(Intent intent)
	{
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
	}
	
}
