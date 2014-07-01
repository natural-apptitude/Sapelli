package uk.ac.ucl.excites.sapelli.collector.ui;

import java.util.ArrayList;
import java.util.List;

import uk.ac.ucl.excites.sapelli.collector.R;
import uk.ac.ucl.excites.sapelli.collector.fragments.ProjectFragment;
import uk.ac.ucl.excites.sapelli.collector.model.Project;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.view.ViewGroup;

public class PagerAdapter extends FragmentPagerAdapter {
	public static String PROJECT = "Project";
	public static String EXPORT = "Export";
	public static String TRANSMISSION = "Transmission";

	String[] tabs = { PROJECT, EXPORT, TRANSMISSION };

	public PagerAdapter(FragmentManager fm) {
		super(fm);
	}

	@Override
	public CharSequence getPageTitle(int position) {

		return tabs[position];
	}

	@Override
	public int getCount() {
		return tabs.length;
	}

	@Override
	public Fragment getItem(int position) {
		return ProjectFragment.newInstance(position);
	}

}
